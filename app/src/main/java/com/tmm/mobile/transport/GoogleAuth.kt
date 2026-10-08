package com.tmm.mobile.transport

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.tmm.mobile.core.AppLog
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

// Acces Drive complet : la boite aux lettres vit dans le dossier "APP" cree par l'utilisateur, que la
// portee drive.file (CdC 7.3.9, fichiers crees par l'app seulement) ne voit pas. Le PC demande la meme.
const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"

// CdC 5.11 : utilise Identity.getAuthorizationClient (pas GoogleSignIn, deprecie).
class GoogleAuth(context: Context) {
    private val client: AuthorizationClient = Identity.getAuthorizationClient(context)

    suspend fun authorize(): AuthorizationResult = suspendCancellableCoroutine { cont ->
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_SCOPE)))
            .build()
        client.authorize(request)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    fun pendingIntent(result: AuthorizationResult): PendingIntent? =
        if (result.hasResolution()) result.pendingIntent else null

    fun resultFromIntent(data: Intent): AuthorizationResult = client.getAuthorizationResultFromIntent(data)

    // BLOQUÉ-2 (leve) : AuthorizationClient.clearToken(token) confirme par la doc officielle
    // (retire le jeton du cache local, renvoie un Task<Void>). Si l'appel echoue quand meme
    // (API modifiee), on ne bloque pas le flux : CdC 5.11 dit de relancer authorize() sans
    // effacement dans ce cas, donc on avale l'erreur et on continue.
    suspend fun clearToken(token: String) {
        try {
            suspendCancellableCoroutine<Unit> { cont ->
                client.clearToken(ClearTokenRequest.builder().setToken(token).build())
                    .addOnSuccessListener { cont.resume(Unit) }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        } catch (e: Exception) {
            AppLog.d("GoogleAuth", "clearToken a echoue, poursuite sans effacement : ${e.message}")
        }
    }
}
