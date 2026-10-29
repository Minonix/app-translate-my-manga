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

/** Bilan d'une recuperation : jobs relances, et jobs laisses a leur travail en cours. */
data class InstallRecoveryReport(val relaunched: Int, val alreadyActive: Int)

/**
 * Relance des installations figees. Apres une OutOfMemoryError, des jobs restaient DOWNLOADING sans
 * travail WorkManager vivant, et rien ne les relancait (refresh ne relance que RESULT_READY, en KEEP).
 *
 * Regles, par job RESULT_READY ou DOWNLOADING :
 * - aucun travail vivant (ni RUNNING, ni ENQUEUED, ni BLOCKED) : relance si updatedAt est plus vieux que
 *   STALL_MINUTES, ou tout de suite si [force] (bouton « Débloquer les téléchargements ») ;
 * - travail vivant : jamais touche, sauf un DOWNLOADING RUNNING sans progression depuis STALL_MINUTES et
 *   qui n'attend pas une place (InstallSlots) - le worker est alors fige malgre son chien de garde.
 *   Un RESULT_READY vivant n'est jamais touche : en attente d'une place ou du reseau, son updatedAt est
 *   vieux par construction.
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
            val relaunch = if (live.isEmpty()) {
                force || stale
            } else {
                job.phoneState == "DOWNLOADING" && stale && !InstallSlots.isWaiting(job.jobId) &&
                    live.any { it.state == WorkInfo.State.RUNNING }
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
