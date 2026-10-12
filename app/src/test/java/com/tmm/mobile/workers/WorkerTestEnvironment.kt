package com.tmm.mobile.workers

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.tmm.mobile.Startup
import com.tmm.mobile.TmmApp
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.ui.screens.library.LibraryCache
import com.tmm.mobile.core.Clock
import com.tmm.mobile.data.AppPrefs
import com.tmm.mobile.data.ChapterDeleter
import com.tmm.mobile.data.JobRepository
import com.tmm.mobile.data.RefreshCoordinator
import com.tmm.mobile.data.SettingsSync
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.data.runAutoTranslate
import com.tmm.mobile.fake.FakeDrive
import com.tmm.mobile.fake.FakePc
import com.tmm.mobile.mihon.MihonFs
import com.tmm.mobile.mihon.MihonRoot
import com.tmm.mobile.transport.DirectTokenStore
import com.tmm.mobile.transport.DirectTransport
import com.tmm.mobile.transport.DriveApi
import com.tmm.mobile.transport.DriveTransport
import com.tmm.mobile.transport.JobTransport
import com.tmm.mobile.transport.TokenCipher
import com.tmm.mobile.transport.TransportRouter
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient

// PLAN_GEMINI.md 5.12 (E8) : environnement de test partage par les tests de workers/JobRepository/
// RefreshCoordinator - construit un AppContainer reel (Room en memoire + FakeDrive/FakePc de l'E7 +
// un Clock pilotable) et le branche sur TmmApp.container (lateinit var substituable, cf AppContainer.kt).
class TestClock(var millis: Long = 1_700_000_000_000L) : Clock {
    override fun nowMillis(): Long = millis
}

// DataStore en memoire pour les tests de ViewModel : le DataStore fichier (teste par AppPrefsTest)
// se bloquait par intermittence quand les collecteurs du ViewModel reprenaient en ligne sur ses
// threads internes (Main de test Unconfined), ce qui n'arrive pas avec le vrai Main.
private class MemoryDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock { transform(state.value).toPreferences().also { state.value = it } }
}

class WorkerTestEnvironment {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db: TmmDatabase = Room.inMemoryDatabaseBuilder(context, TmmDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    val clock = TestClock()
    val drive = FakeDrive { nowIso() }
    lateinit var pc: FakePc
        private set
    lateinit var prefs: AppPrefs
        private set
    lateinit var transport: JobTransport
    lateinit var direct: DirectTransport
    lateinit var router: TransportRouter
        private set
    lateinit var container: FakeAppContainer
        private set

    // Diagnostic : un test bloque plus de 45 s ecrit l'etat de tous les threads sur stderr
    // (visible dans le rapport de test de la CI), avant le timeout de 60 s de runTest.
    private val watchdog = Thread {
        try {
            Thread.sleep(45_000)
            val dump = Thread.getAllStackTraces().entries.joinToString("\n\n") { (t, stack) ->
                "THREAD ${t.name} ${t.state}\n" + stack.take(40).joinToString("\n") { "    at $it" }
            }
            System.err.println("=== WATCHDOG DUMP ===\n$dump")
        } catch (_: InterruptedException) {
        }
    }.apply { isDaemon = true }

    private fun nowIso() = Instant.ofEpochMilli(clock.millis).toString()

    fun setUp() {
        // viewModelScope tourne sur Dispatchers.Main, c'est-a-dire le Looper principal Robolectric,
        // qui n'avance pas pendant runTest : sans ceci, vm.state.first {} attend indefiniment.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        watchdog.start()
        drive.start()
        pc = FakePc(drive) { nowIso() }
        prefs = AppPrefs(MemoryDataStore())
        val api = DriveApi(client = OkHttpClient(), token = { _ -> "test-token" }, baseUrl = drive.baseUrl())
        transport = DriveTransport(api, prefs)
        direct = DirectTransport(OkHttpClient(), prefs, DirectTokenStore(prefs, FakeTokenCipher))
        router = TransportRouter(transport, direct)
        // ensureLayout() une fois pour toutes : SendJobWorker/InstallJobWorker/OutboxWorker
        // supposent le layout Drive deja etabli (seul RefreshCoordinator.refresh() l'appelle en prod).
        kotlinx.coroutines.runBlocking { transport.ensureLayout() }
        // ponytail: initializeTestWorkManager remplace le singleton eventuellement deja initialise
        // par androidx.startup (via le manifest fusionne) sans lever d'exception - c'est precisement
        // son role documente pour les tests JVM/Robolectric (cf commentaire build.gradle.kts).
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        container = FakeAppContainer(context, db, prefs, router, clock)
        (context as TmmApp).container = container
    }

    fun tearDown() {
        watchdog.interrupt()
        Dispatchers.resetMain()
        drive.shutdown()
        db.close()
    }
}

// Meme cablage que DefaultAppContainer (AppContainer.kt), avec transport/clock substitues.
class FakeAppContainer(
    override val context: Context,
    override val db: TmmDatabase,
    override val prefs: AppPrefs,
    override val router: TransportRouter,
    override val clock: Clock,
) : AppContainer {
    override val transport: JobTransport get() = router.drive
    override val workManager: WorkManager by lazy { WorkManager.getInstance(context) }
    private val scope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    override val jobRepository: JobRepository by lazy { JobRepository(db, prefs, router, workManager, clock) }
    override val refreshCoordinator: RefreshCoordinator by lazy {
        RefreshCoordinator(
            db, prefs, router, workManager, clock, scope,
            resendStalledDriveJobs = { jobRepository.resendStalledDriveJobs() },
            onSettings = { settingsSync.reconcile(it) },
        ) { runAutoTranslate(db, jobRepository) }
    }
    override val settingsSync: SettingsSync by lazy { SettingsSync(prefs, router, workManager) }
    // Arbre Mihon substituable (FakeMihonFs) ; null = aucun arbre SAF configure.
    var mihonFs: MihonFs? = null
    // docId de la racine de l'arbre (FakeMihonFs : chemin absolu du dossier racine).
    var mihonRootDocId: String? = null
    override suspend fun mihonRoot(): MihonRoot? {
        val fs = mihonFs ?: return null
        val root = mihonRootDocId ?: return null
        return MihonRoot(fs, root)
    }
    override val chapterDeleter: ChapterDeleter by lazy { ChapterDeleter(db, settingsSync) { mihonFs } }
    override val startup: Startup by lazy { Startup(context, db, prefs, jobRepository, refreshCoordinator) }
    // Fichier neuf par test : un instantane d'un test precedent ne doit pas s'afficher dans le suivant.
    override val libraryCache: LibraryCache by lazy { LibraryCache(db, scope, java.io.File(kotlin.io.path.createTempDirectory().toFile(), "library_snapshot.json")) }
}

// Robolectric n'a pas d'Android Keystore : chiffreur factice (aucun secret reel dans les tests).
object FakeTokenCipher : TokenCipher {
    override fun encrypt(plain: String): String = "enc:$plain"
    override fun decrypt(encoded: String): String? = encoded.removePrefix("enc:").takeIf { encoded.startsWith("enc:") }
}
