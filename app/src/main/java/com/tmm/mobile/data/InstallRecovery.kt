package com.tmm.mobile.data

import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.tmm.mobile.core.AppLog
import com.tmm.mobile.core.Clock
import com.tmm.mobile.data.db.TmmDatabase
import com.tmm.mobile.workers.InstallSlots
import com.tmm.mobile.workers.enqueueInstallJob
import com.tmm.mobile.workers.installWorkName
import kotlinx.coroutines.flow.first

// Installation sans progression depuis ce delai (horloge Clock) : consideree figee.
const val STALL_MINUTES = 10L
private const val STALL_MILLIS = STALL_MINUTES * 60_000L
// Bouton « Débloquer » : un travail vivant n'est epargne que s'il a progresse dans ce delai (ou attend une place).
private const val FORCE_FRESH_MILLIS = 30_000L

/** Bilan d'une recuperation : jobs relances, et jobs laisses a leur travail en cours. */
data class InstallRecoveryReport(val relaunched: Int, val alreadyActive: Int)

/**
 * Relance des installations figees. Apres une OutOfMemoryError, des jobs restaient DOWNLOADING sans
 * travail WorkManager vivant, et rien ne les relancait (refresh ne relance que RESULT_READY, en KEEP).
 *
 * Regles, par job RESULT_READY ou DOWNLOADING :
 * - aucun travail vivant (ni RUNNING, ni ENQUEUED, ni BLOCKED) : relance si updatedAt est plus vieux que
 *   STALL_MINUTES, ou tout de suite si [force] (bouton « Débloquer les téléchargements ») ;
 * - travail vivant, sans [force] : relance tout job sans progression depuis STALL_MINUTES qui n'attend pas
 *   une place (InstallSlots) : worker fige, ou ENQUEUED dans un backoff exponentiel de plusieurs heures
 *   (cas v1.16 : jobs bloques indefiniment). REPLACE remet le backoff a zero ; sans reseau, la contrainte
 *   garde simplement le nouveau travail en attente.
 * - travail vivant, avec [force] : relance tout ce qui n'a pas progresse depuis FORCE_FRESH_MILLIS, sauf un
 *   worker qui attend une place. Le bouton servait a rien sur des jobs « actifs » figes (backoff de plusieurs
 *   heures, worker bloque) : il les comptait comme actifs sans les relancer.
 * Une relance remet la progression a zero (pas de reprise Range) et date le job de maintenant : il compte
 * ensuite comme « actif + progression recente » et n'est pas relance une seconde fois au poll suivant.
 */
class InstallRecovery(
    private val db: TmmDatabase,
    private val prefs: AppPrefs,
    private val workManager: WorkManager,
    private val clock: Clock,
) {
    suspend fun recover(force: Boolean = false): InstallRecoveryReport {
        val pending = db.jobDao().observeAll().first().filter { it.phoneState == "RESULT_READY" || it.phoneState == "DOWNLOADING" }
        if (pending.isEmpty()) return InstallRecoveryReport(0, 0)
        val wifiOnly = prefs.wifiOnly.first()
        var relaunched = 0
        var active = 0
        for (job in pending) {
            val now = clock.nowMillis()
            val stale = now - job.updatedAt >= STALL_MILLIS
            val live = workManager.getWorkInfosForUniqueWorkFlow(installWorkName(job.jobId)).first()
                .filter { !it.state.isFinished }
            val waitingSlot = InstallSlots.isWaiting(job.jobId)
            val relaunch = when {
                live.isEmpty() -> force || stale
                waitingSlot -> false
                force -> now - job.updatedAt >= FORCE_FRESH_MILLIS
                else -> stale
            }
            if (force || relaunch) {
                AppLog.d("InstallRecovery", "${job.jobId.take(8)} ${job.phoneState} travail=${live.map { it.state }} " +
                    "attente=$waitingSlot inactif=${(now - job.updatedAt) / 1000}s -> ${if (relaunch) "relance" else "garde"}")
            }
            if (!relaunch) {
                if (live.isNotEmpty()) active++
                continue
            }
            // Relu juste avant l'ecriture : le job a pu etre installe ou oublie depuis la lecture de la liste.
            val current = db.jobDao().get(job.jobId)
                ?.takeIf { it.phoneState == "RESULT_READY" || it.phoneState == "DOWNLOADING" } ?: continue
            db.jobDao().upsert(current.copy(transferredBytes = 0, updatedAt = now))
            workManager.enqueueInstallJob(job.jobId, wifiOnly, ExistingWorkPolicy.REPLACE)
            relaunched++
        }
        if (relaunched > 0) AppLog.d("InstallRecovery", "$relaunched installation(s) figee(s) relancee(s), $active deja active(s)")
        return InstallRecoveryReport(relaunched, active)
    }
}
