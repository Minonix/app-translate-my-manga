package com.tmm.mobile.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.tmm.mobile.R
import com.tmm.mobile.data.status.LocalErrorCode
import com.tmm.mobile.transport.DirectPairingExpiredException
import com.tmm.mobile.transport.DriveQuotaException
import java.util.concurrent.TimeUnit

// PLAN_GEMINI.md 5.12 : constantes et helpers partages par les 3 workers (noms de travail
// uniques, contraintes reseau, notification "Transferts", backoff exponentiel).
const val KEY_JOB_ID = "jobId"
const val NOTIFICATION_CHANNEL_TRANSFERS = "transfers"
const val BACKOFF_DELAY_SECONDS = 30L

// CdC 11.2 : a partir de la 6e tentative (runAttemptCount commence a 0), on abandonne en FAILED
// plutot que de reessayer indefiniment.
const val MAX_RETRY_ATTEMPTS = 5

/** Chapitre Mihon supprime ou illisible (code local `source_unreadable`) : inutile de reessayer. */
class SourceUnreadableException(message: String) : Exception(message)

/** docs/ERREURS_PROTOCOLE.md §1.2 : code local d'un echec de worker. */
fun localErrorCode(e: Throwable): String = when {
    e is DirectPairingExpiredException -> LocalErrorCode.PAIRING
    e is DriveQuotaException -> LocalErrorCode.DRIVE_QUOTA
    e is SecurityException -> LocalErrorCode.PERMISSION
    e is SourceUnreadableException -> LocalErrorCode.SOURCE_UNREADABLE
    isDiskFull(e) -> LocalErrorCode.PHONE_DISK_FULL
    else -> LocalErrorCode.NETWORK
}

// ENOSPC remonte en IOException (« No space left on device »), parfois enveloppee.
fun isDiskFull(e: Throwable): Boolean = generateSequence(e) { it.cause }.take(5).any {
    val message = it.message.orEmpty()
    message.contains("ENOSPC") || message.contains("No space left", ignoreCase = true)
}

fun sendWorkName(jobId: String) = "send-$jobId"
fun installWorkName(jobId: String) = "install-$jobId"
const val OUTBOX_WORK_NAME = "outbox"

fun buildConstraints(wifiOnly: Boolean): Constraints = Constraints.Builder()
    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
    .build()

// REPLACE sert a relancer tout de suite un envoi en attente de backoff (renvoi en direct, voir
// JobRepository.resendStalledDriveJobs) ; KEEP partout ailleurs.
fun WorkManager.enqueueSendJob(jobId: String, wifiOnly: Boolean, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
    val request = OneTimeWorkRequestBuilder<SendJobWorker>()
        .setConstraints(buildConstraints(wifiOnly))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setInputData(Data.Builder().putString(KEY_JOB_ID, jobId).build())
        .build()
    enqueueUniqueWork(sendWorkName(jobId), policy, request)
}

fun WorkManager.enqueueInstallJob(jobId: String, wifiOnly: Boolean) {
    val request = OneTimeWorkRequestBuilder<InstallJobWorker>()
        .setConstraints(buildConstraints(wifiOnly))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setInputData(Data.Builder().putString(KEY_JOB_ID, jobId).build())
        .build()
    enqueueUniqueWork(installWorkName(jobId), ExistingWorkPolicy.KEEP, request)
}

fun WorkManager.enqueueOutbox(wifiOnly: Boolean) {
    val request = OneTimeWorkRequestBuilder<OutboxWorker>()
        .setConstraints(buildConstraints(wifiOnly))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
        .build()
    enqueueUniqueWork(OUTBOX_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
}

fun ensureTransferChannel(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    // Appel sans test d'existence : createNotificationChannel met a jour le nom d'un canal deja cree
    // (l'ancien « Transferts ») sans toucher aux reglages que l'utilisateur y a faits.
    manager.createNotificationChannel(
        NotificationChannel(
            NOTIFICATION_CHANNEL_TRANSFERS,
            context.getString(R.string.notification_channel_transfers),
            NotificationManager.IMPORTANCE_LOW,
        ),
    )
}

fun transferForegroundInfo(context: Context, notificationId: Int, @StringRes title: Int): ForegroundInfo {
    ensureTransferChannel(context)
    val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_TRANSFERS)
        .setContentTitle(context.getString(title))
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setOngoing(true)
        .build()
    return ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
}
