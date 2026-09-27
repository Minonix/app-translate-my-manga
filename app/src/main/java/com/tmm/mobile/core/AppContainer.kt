package com.tmm.mobile.core

import android.content.Context
import androidx.work.WorkManager
import com.tmm.mobile.Startup
import com.tmm.mobile.data.AppPrefs
import com.tmm.mobile.data.JobRepository
import com.tmm.mobile.data.RefreshCoordinator
import com.tmm.mobile.data.SettingsSync
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.data.runAutoTranslate
import com.tmm.mobile.data.tmmDataStore
import com.tmm.mobile.transport.AuthExpiredException
import com.tmm.mobile.transport.DriveApi
import com.tmm.mobile.transport.DriveTransport
import com.tmm.mobile.transport.GoogleAuth
import com.tmm.mobile.transport.JobTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

interface AppContainer {
    val context: Context
    val db: TmmDatabase
    val prefs: AppPrefs
    val clock: Clock
    val workManager: WorkManager
    val transport: JobTransport
    val jobRepository: JobRepository
    val refreshCoordinator: RefreshCoordinator
    val settingsSync: SettingsSync
    val startup: Startup
}

// PLAN_GEMINI.md : cablage complet de l'E8 (pas de framework de DI - singletons lazy manuels, comme
// le reste du projet). WorkManager utilise sa Configuration par defaut (androidx.startup) : les 3
// workers (CoroutineWorker simples) n'ont pas besoin d'un WorkerFactory personnalise puisqu'ils lisent
// leurs dependances via (applicationContext as TmmApp).container plutot que par injection au constructeur.
class DefaultAppContainer(override val context: Context) : AppContainer {
    override val db: TmmDatabase by lazy { TmmDatabase.build(context) }
    override val prefs: AppPrefs by lazy { AppPrefs(context.tmmDataStore) }
    override val clock: Clock by lazy { SystemClock }
    override val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    private val scope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    private val googleAuth: GoogleAuth by lazy { GoogleAuth(context) }
    private val tokenMutex = Mutex()
    private var cachedToken: String? = null

    // ponytail: pas de rafraichissement silencieux via refresh token (l'API Identity ne l'expose
    // pas ici) - authorize() est rejoue et son resultat peut demander une resolution UI ; en dehors
    // d'une Activity (workers), on ne peut pas la lancer donc on remonte AuthExpiredException pour
    // que RefreshCoordinator/les workers marquent authExpired et laissent l'ecran de permission (E10)
    // relancer le consentement au premier plan.
    private suspend fun provideToken(forceRefresh: Boolean): String = tokenMutex.withLock {
        val cached = cachedToken
        if (!forceRefresh && cached != null) return@withLock cached
        val result = googleAuth.authorize()
        val token = result.accessToken ?: throw AuthExpiredException("consentement Google requis")
        cachedToken = token
        token
    }

    private val driveApi: DriveApi by lazy {
        DriveApi(
            client = OkHttpClient(),
            token = ::provideToken,
            baseUrl = "https://www.googleapis.com/".toHttpUrl(),
        )
    }

    override val transport: JobTransport by lazy { DriveTransport(driveApi, prefs) }

    override val jobRepository: JobRepository by lazy { JobRepository(db, prefs, transport, workManager, clock) }

    override val refreshCoordinator: RefreshCoordinator by lazy {
        RefreshCoordinator(db, prefs, transport, workManager, clock, scope) { runAutoTranslate(db, jobRepository) }
    }

    override val settingsSync: SettingsSync by lazy { SettingsSync(prefs, transport, workManager) }

    override val startup: Startup by lazy { Startup(context, db, prefs, jobRepository, refreshCoordinator) }
}
