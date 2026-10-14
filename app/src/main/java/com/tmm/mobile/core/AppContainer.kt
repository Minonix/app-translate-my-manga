package com.tmm.mobile.core

import android.content.Context
import android.net.Uri
import androidx.work.WorkManager
import com.tmm.mobile.Startup
import com.tmm.mobile.data.AppPrefs
import com.tmm.mobile.data.ChapterDeleter
import com.tmm.mobile.data.JobRepository
import com.tmm.mobile.data.RefreshCoordinator
import com.tmm.mobile.data.SettingsSync
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.data.runAutoTranslate
import com.tmm.mobile.data.tmmDataStore
import com.tmm.mobile.mihon.MihonRoot
import com.tmm.mobile.mihon.MihonTree
import com.tmm.mobile.transport.AuthExpiredException
import com.tmm.mobile.transport.DirectTokenStore
import com.tmm.mobile.transport.DirectTransport
import com.tmm.mobile.transport.KeystoreTokenCipher
import com.tmm.mobile.transport.TransportRouter
import com.tmm.mobile.transport.DriveApi
import com.tmm.mobile.transport.DriveTransport
import com.tmm.mobile.transport.driveHttpClient
import com.tmm.mobile.transport.GoogleAuth
import com.tmm.mobile.transport.JobTransport
import com.tmm.mobile.ui.screens.library.LibraryCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

interface AppContainer {
    val context: Context
    val db: TmmDatabase
    val prefs: AppPrefs
    val clock: Clock
    val workManager: WorkManager
    // Canal Drive (toujours disponible) ; le choix Drive/direct par job ou par operation passe par router.
    val transport: JobTransport
    val router: TransportRouter
    val jobRepository: JobRepository
    val refreshCoordinator: RefreshCoordinator
    val settingsSync: SettingsSync
    val chapterDeleter: ChapterDeleter
    val startup: Startup
    val libraryCache: LibraryCache
    val appUpdater: AppUpdater

    /** Arbre Mihon (SAF) configure, ou null s'il n'y en a pas. Point d'injection des workers pour les tests. */
    suspend fun mihonRoot(): MihonRoot?
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
        // R13 : jeton refuse par Drive (401) : invalide aupres de Play Services avant d'en redemander un.
        if (forceRefresh && cached != null) {
            cachedToken = null
            googleAuth.clearToken(cached)
        }
        // R13 : toute exception de l'autorisation (Play Services hors ligne, compte retire...) est `auth` :
        // retry + bandeau « Reconnecter », jamais un FAILED definitif.
        val result = try {
            googleAuth.authorize()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AuthExpiredException("autorisation Google impossible : ${e.javaClass.simpleName}")
        }
        val token = result.accessToken ?: throw AuthExpiredException("consentement Google requis")
        cachedToken = token
        // Jeton obtenu = consentement valide : le bandeau "Reconnecter" tombe tout de suite, sans
        // attendre un poll Drive complet qu'une autre erreur (dossier, reseau) pouvait bloquer.
        if (prefs.authExpired.first()) prefs.setAuthExpired(false)
        token
    }

    private suspend fun rejectToken() = tokenMutex.withLock {
        val cached = cachedToken ?: return@withLock
        cachedToken = null
        googleAuth.clearToken(cached)
    }

    private val driveApi: DriveApi by lazy {
        DriveApi(
            client = driveHttpClient(),
            token = ::provideToken,
            baseUrl = "https://www.googleapis.com/".toHttpUrl(),
            onTokenRejected = ::rejectToken,
        )
    }

    override val transport: JobTransport by lazy { DriveTransport(driveApi, prefs) }

    // Delais courts pour les appels de controle, longs pour les transferts de CBZ (le health-check a son
    // propre client de 2 s dans DirectTransport).
    private val directClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    override val router: TransportRouter by lazy {
        TransportRouter(transport, DirectTransport(directClient, prefs, DirectTokenStore(prefs, KeystoreTokenCipher())))
    }

    override val jobRepository: JobRepository by lazy { JobRepository(db, prefs, router, workManager, clock) }

    override val refreshCoordinator: RefreshCoordinator by lazy {
        RefreshCoordinator(
            db, prefs, router, workManager, clock, scope,
            resendStalledDriveJobs = { jobRepository.resendStalledDriveJobs() },
            onSettings = { settingsSync.reconcile(it) },
        ) { runAutoTranslate(db, jobRepository) }
    }

    override val settingsSync: SettingsSync by lazy { SettingsSync(prefs, router, workManager) }

    override val chapterDeleter: ChapterDeleter by lazy {
        ChapterDeleter(db, settingsSync) {
            prefs.mihonTreeUri.first()?.let { MihonTree(context.contentResolver, Uri.parse(it)) }
        }
    }

    override val startup: Startup by lazy { Startup(context, db, prefs, jobRepository, refreshCoordinator) }

    override val libraryCache: LibraryCache by lazy { LibraryCache(db, scope, java.io.File(context.filesDir, "library_snapshot.json")) }
    // Dossier partage avec l'installateur via FileProvider (res/xml/file_paths.xml).
    override val appUpdater: AppUpdater by lazy { AppUpdater(directClient, java.io.File(context.cacheDir, "updates")) }

    override suspend fun mihonRoot(): MihonRoot? {
        val treeUri = prefs.mihonTreeUri.first()?.let { Uri.parse(it) } ?: return null
        return MihonRoot(MihonTree(context.contentResolver, treeUri), android.provider.DocumentsContract.getTreeDocumentId(treeUri))
    }
}
