package com.tmm.mobile.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.tmm.mobile.TmmApp
import com.tmm.mobile.core.Clock
import com.tmm.mobile.data.Retention
import com.tmm.mobile.data.db.JobEntity
import com.tmm.mobile.protocol.CancelJson
import com.tmm.mobile.protocol.ReceivedJson
import com.tmm.mobile.transport.AuthExpiredException
import com.tmm.mobile.transport.JobTransport
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.first

// PLAN_GEMINI.md 5.12/CdC 7.3.10 : ecrit cancel.json/received.json pour les jobs qui les doivent
// encore, applique la retention Drive. La confirmation des SettingsCommand (pending_commands_json)
// est deferree a E9/SettingsSync : on ne les touche pas ici.
class OutboxWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as TmmApp).container
        val db = container.db
        val transport = container.transport
        val clock = container.clock
        val prefs = container.prefs

        return try {
            val now = Instant.now().toString()
            val retention = prefs.retention.first()

            for (job in db.jobDao().observeAll().first()) {
                if (job.phoneState == "CANCEL_REQUESTED" && job.cancelWrittenAt == null) {
                    transport.writeCancel(job, CancelJson(jobId = job.jobId, requestedAt = now))
                    db.jobDao().upsert(job.copy(cancelWrittenAt = clock.nowMillis(), updatedAt = clock.nowMillis()))
                }
            }

            for (job in db.jobDao().observeAll().first()) {
                if (job.phoneState == "INSTALLED" && job.receivedWrittenAt == null) {
                    val installedPath = requireNotNull(job.installedPath) { "job INSTALLED sans installedPath" }
                    transport.writeReceived(job, ReceivedJson(jobId = job.jobId, receivedAt = now, installedPath = installedPath))
                    var updated = job.copy(receivedWrittenAt = clock.nowMillis(), updatedAt = clock.nowMillis())
                    if (retention == Retention.AFTER_RECEIVE) {
                        transport.deleteJobFolder(updated)
                        updated = updated.copy(driveDeletedAt = clock.nowMillis(), updatedAt = clock.nowMillis())
                    }
                    db.jobDao().upsert(updated)
                }
            }

            applyDays30Retention(retention, transport, db.jobDao().observeAll().first(), clock)
            Result.success()
        } catch (e: AuthExpiredException) {
            Result.retry()
        } catch (e: IOException) {
            if (runAttemptCount >= MAX_RETRY_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    // ponytail: pas de planification separee pour la retention 30 jours - un balayage a chaque
    // passage de l'outbox suffit (l'outbox tourne a chaque cycle de refresh/cancel/received) ;
    // upgrade vers un WorkRequest periodique dedie si un delai plus strict est requis.
    private suspend fun applyDays30Retention(
        retention: Retention,
        transport: JobTransport,
        jobs: List<JobEntity>,
        clock: Clock,
    ) {
        if (retention != Retention.DAYS30) return
        val cutoff = clock.nowMillis() - 30L * 24 * 60 * 60 * 1000
        val db = (applicationContext as TmmApp).container.db
        for (job in jobs) {
            if (job.driveDeletedAt == null && job.receivedWrittenAt != null && job.receivedWrittenAt <= cutoff) {
                transport.deleteJobFolder(job)
                db.jobDao().upsert(job.copy(driveDeletedAt = clock.nowMillis(), updatedAt = clock.nowMillis()))
            }
        }
    }
}
