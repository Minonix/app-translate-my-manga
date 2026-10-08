package com.tmm.mobile.ui.components

import android.content.Context
import coil3.ImageLoader
import coil3.network.HttpException
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

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
        return chain.proceed(
            request.newBuilder()
                .header("User-Agent", COVER_USER_AGENT)
                .header("Referer", "${url.scheme}://${url.host}/")
                .build(),
        )
    }
}

/** ImageLoader de l'app : client OkHttp dedie aux couvertures, construit au premier chargement reseau. */
fun newCoverImageLoader(context: Context): ImageLoader {
    val client by lazy { OkHttpClient.Builder().addInterceptor(CoverHeadersInterceptor()).build() }
    return ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
        .build()
}
