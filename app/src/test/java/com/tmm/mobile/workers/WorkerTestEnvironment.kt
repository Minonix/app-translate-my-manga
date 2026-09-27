package com.tmm.mobile.workers

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.tmm.mobile.Startup
import com.tmm.mobile.TmmApp
import com.tmm.mobile.core.AppContainer
import com.tmm.mobile.core.Clock
import com.tmm.mobile.data.AppPrefs
import com.tmm.mobile.data.JobRepository
import com.tmm.mobile.data.RefreshCoordinator
import com.tmm.mobile.data.SettingsSync
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.data.runAutoTranslate
import com.tmm.mobile.fake.FakeDrive
import com.tmm.mobile.fake.FakePc
import com.tmm.mobile.transport.DriveApi
import com.tmm.mobile.transport.DriveTransport
import com.tmm.mobile.transport.JobTransport
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        private set
    lateinit var container: FakeAppContainer
        private set

    private fun nowIso() = Instant.ofEpochMilli(clock.millis).toString()

    fun setUp() {
        // viewModelScope tourne sur Dispatchers.Main, c'est-a-dire le Looper principal Robolectric,
        // qui n'avance pas pendant runTest : sans ceci, vm.state.first {} attend indefiniment.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        drive.start()
        pc = FakePc(drive) { nowIso() }
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(context.filesDir, "worker_test_prefs_${UUID.randomUUID()}.preferences_pb") },
        )
        prefs = AppPrefs(dataStore)
        val api = DriveApi(client = OkHttpClient(), token = { _ -> "test-token" }, baseUrl = drive.baseUrl())
        transport = DriveTransport(api, prefs)
        // ensureLayout() une fois pour toutes : SendJobWorker/InstallJobWorker/OutboxWorker
        // supposent le layout Drive deja etabli (seul RefreshCoordinator.refresh() l'appelle en prod).
        kotlinx.coroutines.runBlocking { transport.ensureLayout() }
        // ponytail: initializeTestWorkManager remplace le singleton eventuellement deja initialise
        // par androidx.startup (via le manifest fusionne) sans lever d'exception - c'est precisement
        // son role documente pour les tests JVM/Robolectric (cf commentaire build.gradle.kts).
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        container = FakeAppContainer(context, db, prefs, transport, clock)
        (context as TmmApp).container = container
    }

    fun tearDown() {
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
    override val transport: JobTransport,
    override val clock: Clock,
) : AppContainer {
    override val workManager: WorkManager by lazy { WorkManager.getInstance(context) }
    private val scope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    override val jobRepository: JobRepository by lazy { JobRepository(db, prefs, transport, workManager, clock) }
    override val refreshCoordinator: RefreshCoordinator by lazy {
        RefreshCoordinator(db, prefs, transport, workManager, clock, scope) { runAutoTranslate(db, jobRepository) }
    }
    override val settingsSync: SettingsSync by lazy { SettingsSync(prefs, transport, workManager) }
    override val startup: Startup by lazy { Startup(context, db, prefs, jobRepository, refreshCoordinator) }
}
