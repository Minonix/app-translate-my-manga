package com.tmm.mobile.ui.components

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.tmm.mobile.mihon.CbzPacker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil3.network.HttpException
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.Buffer
import java.io.FileNotFoundException
import java.net.URLDecoder

// Chargement des couvertures (thumbnailUrl du .tachibk). Certains CDN refusent les requetes sans
// User-Agent de navigateur ou sans Referer : d'ou un client OkHttp dedie, pose par TmmApp comme
// ImageLoader singleton de Coil.

// User-Agent d'un Chrome mobile Android recent : les CDN de scans filtrent souvent "okhttp/x.y".
internal const val COVER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

/**
 * URL de couverture prete pour Coil, ou null si inutilisable : vide/blanche, schema autre que
 * http(s), ou hote absent. http:// passe en https:// (cleartext interdit par defaut sur Android 9+).
 */
internal fun normalizeCoverUrl(raw: String?): String? {
    val url = raw?.trim().orEmpty()
    val normalized = when {
        url.startsWith("https://", ignoreCase = true) -> url
        url.startsWith("http://", ignoreCase = true) -> "https://" + url.substring("http://".length)
        else -> return null
    }
    return normalized.takeIf { it.toHttpUrlOrNull() != null }
}

private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

/**
 * Document id d'une couverture de la source locale de Mihon (`content://com.android.externalstorage
 * .documents/tree/.../document/primary%3AMihon%2Flocal%2F<serie>%2Fcover.jpg`), ou null si `raw` n'en
 * est pas une. Mihon note ces URI sous SA permission d'arborescence : on ne garde que le document id
 * pour le relire sous celle de TMM.
 */
internal fun externalStorageCoverDocId(raw: String?): String? {
    val url = raw?.trim().orEmpty()
    if (!url.startsWith("content://$EXTERNAL_STORAGE_AUTHORITY/", ignoreCase = true)) return null
    val encoded = url.substringAfter("/document/", "").substringBefore('?').takeIf { it.isNotEmpty() } ?: return null
    // URLDecoder transforme '+' en espace : on le protege, les URI SAF encodent l'espace en %20.
    return runCatching { URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8") }.getOrNull()
}

/** Vrai si l'arborescence `treeDocId` (ex. "primary:Mihon" ou "primary:") contient le document. */
internal fun treeContains(treeDocId: String, docId: String): Boolean =
    docId == treeDocId || docId.startsWith(if (treeDocId.endsWith(":")) treeDocId else "$treeDocId/")

/**
 * URI lisible par TMM d'une couverture de la source locale Mihon : meme document, reconstruit sous
 * une permission d'arborescence que TMM detient (le dossier Mihon choisi a l'onboarding). Null si
 * aucune ne le couvre : la couverture reste alors en placeholder.
 */
internal fun localCoverUri(context: Context, raw: String?): Uri? {
    val docId = externalStorageCoverDocId(raw) ?: return null
    val tree = readableTreeFor(context, docId) ?: return null
    return DocumentsContract.buildDocumentUriUsingTree(tree, docId)
}

/** Arborescence SAF persistee par TMM qui contient `docId`, ou null. */
private fun readableTreeFor(context: Context, docId: String): Uri? = context.contentResolver.persistedUriPermissions
    .filter { it.isReadPermission && it.uri.authority == EXTERNAL_STORAGE_AUTHORITY }
    .map { it.uri }
    .firstOrNull { runCatching { treeContains(DocumentsContract.getTreeDocumentId(it), docId) }.getOrDefault(false) }

private const val PAGE_COVER_PREFIX = "tmm-page:"

/**
 * Couverture de repli : premiere page d'un chapitre telecharge (`docId` SAF du .cbz ou du dossier).
 * Sert quand la sauvegarde n'a pas d'URL (serie vue seulement dans downloads/) ou que le CDN la refuse.
 */
fun pageCoverRef(docId: String, isCbz: Boolean): String = "$PAGE_COVER_PREFIX${if (isCbz) "cbz" else "dir"}:$docId"

/** Modele Coil d'une couverture de repli, lu par [ChapterPageCoverFetcher]. */
data class ChapterPageCover(val tree: Uri, val docId: String, val isCbz: Boolean)

internal fun parsePageCoverRef(ref: String?): Pair<String, Boolean>? {
    if (ref == null || !ref.startsWith(PAGE_COVER_PREFIX)) return null
    val rest = ref.removePrefix(PAGE_COVER_PREFIX)
    val kind = rest.substringBefore(':')
    val docId = rest.substringAfter(':', "").takeIf { it.isNotEmpty() } ?: return null
    return when (kind) {
        "cbz" -> docId to true
        "dir" -> docId to false
        else -> null
    }
}

internal fun pageCoverModel(context: Context, ref: String?): ChapterPageCover? {
    val (docId, isCbz) = parsePageCoverRef(ref) ?: return null
    val tree = readableTreeFor(context, docId) ?: return null
    return ChapterPageCover(tree, docId, isCbz)
}

/** Lit la premiere page d'un chapitre telecharge pour Coil. */
internal class ChapterPageCoverFetcher(
    private val context: Context,
    private val data: ChapterPageCover,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bytes = if (data.isCbz) {
            val uri = DocumentsContract.buildDocumentUriUsingTree(data.tree, data.docId)
            resolver.openInputStream(uri)?.use { CbzPacker.firstPage(it)?.second }
        } else {
            firstDirPage(data)?.let { page -> resolver.openInputStream(page)?.use { it.readBytes() } }
        } ?: throw FileNotFoundException("aucune page")
        SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    private fun firstDirPage(data: ChapterPageCover): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(data.tree, data.docId)
        val pages = mutableListOf<Pair<String, String>>()
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        context.contentResolver.query(children, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (CbzPacker.isEligible(name) && CbzPacker.isImage(name)) pages += name to c.getString(0)
            }
        }
        val first = pages.minWithOrNull(compareBy(CbzPacker.NATURAL_ORDER) { it.first }) ?: return null
        return DocumentsContract.buildDocumentUriUsingTree(data.tree, first.second)
    }

    class Factory(private val context: Context) : Fetcher.Factory<ChapterPageCover> {
        override fun create(data: ChapterPageCover, options: Options, imageLoader: ImageLoader): Fetcher =
            ChapterPageCoverFetcher(context, data, options)
    }
}

/** Cle du cache memoire de Coil : sans elle, chaque retour sur l'ecran relirait le chapitre. */
internal object ChapterPageCoverKeyer : Keyer<ChapterPageCover> {
    override fun key(data: ChapterPageCover, options: Options): String = "tmm-page:${data.docId}"
}

/** Ligne de journal d'un echec de couverture : hote, code HTTP et classe d'erreur, jamais l'URL. */
internal fun coverErrorLogLine(url: String, throwable: Throwable): String {
    val host = url.toHttpUrlOrNull()?.host ?: "?"
    val code = (throwable as? HttpException)?.response?.code
    return "couverture non chargee hote=$host" + (code?.let { " http=$it" } ?: "") + " erreur=${throwable::class.java.simpleName}"
}

/** Pose un User-Agent de navigateur mobile et un Referer sur chaque requete de couverture. */
internal class CoverHeadersInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        // ponytail: Referer = racine de l'hote de l'image, approximation. Beaucoup de CDN attendent le
        // domaine du site source, que le .tachibk ne fournit pas ; a revoir si la source devient connue
        // (ex. table source -> baseUrl), ou par source si des couvertures restent bloquees.
        val withUa = request.newBuilder().header("User-Agent", COVER_USER_AGENT)
        val response = chain.proceed(withUa.header("Referer", "${url.scheme}://${url.host}/").build())
        // Un CDN anti-hotlink refuse un Referer etranger a son site mais accepte souvent une requete sans.
        if (response.code != 401 && response.code != 403) return response
        response.close()
        return chain.proceed(withUa.removeHeader("Referer").build())
    }
}

/**
 * Suit les redirections en forcant https. OkHttp suivait sinon un 301 vers http:// (ex.
 * cdn.statically.io) et Android refusait le cleartext : UnknownServiceException, couverture vide.
 * Le client doit avoir followRedirects(false) ; un intercepteur applicatif peut rappeler proceed().
 */
internal class CoverHttpsRedirectInterceptor(private val maxHops: Int = 5) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var response = chain.proceed(request)
        var hops = 0
        while (response.isRedirect && hops < maxHops) {
            val target = response.header("Location")?.let { request.url.resolve(it) } ?: break
            val secure = if (target.scheme == "http") target.newBuilder().scheme("https").build() else target
            response.close()
            request = request.newBuilder().url(secure).build()
            response = chain.proceed(request)
            hops++
        }
        return response
    }
}

/** Client OkHttp des couvertures (en-tetes de navigateur, redirections forcees en https). */
fun newCoverHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false)
    .followSslRedirects(false)
    // Redirections d'abord : chaque saut repasse par les en-tetes (Referer de son hote).
    .addInterceptor(CoverHttpsRedirectInterceptor())
    .addInterceptor(CoverHeadersInterceptor())
    .build()

/** ImageLoader de l'app : client OkHttp dedie aux couvertures, construit au premier chargement reseau. */
fun newCoverImageLoader(context: Context): ImageLoader {
    val client by lazy { newCoverHttpClient() }
    return ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { client }))
            add(ChapterPageCoverFetcher.Factory(context.applicationContext))
            add(ChapterPageCoverKeyer)
        }
        .build()
}
