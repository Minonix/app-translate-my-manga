package com.tmm.mobile.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
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
import java.util.concurrent.TimeUnit

// PLAN_GEMINI.md 5.12 : constantes et helpers partages par les 3 workers (noms de travail
// uniques, contraintes reseau, notification "Transferts", backoff exponentiel).
const val KEY_JOB_ID = "jobId"
const val NOTIFICATION_CHANNEL_TRANSFERS = "transfers"
const val BACKOFF_DELAY_SECONDS = 30L

// CdC 11.2 : a partir de la 6e tentative (runAttemptCount commence a 0), on abandonne en FAILED
// plutot que de reessayer indefiniment.
const val MAX_RETRY_ATTEMPTS = 5

fun sendWorkName(jobId: String) = "send-$jobId"
fun installWorkName(jobId: String) = "install-$jobId"
const val OUTBOX_WORK_NAME = "outbox"

fun buildConstraints(wifiOnly: Boolean): Constraints = Constraints.Builder()
    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
    .build()

fun WorkManager.enqueueSendJob(jobId: String, wifiOnly: Boolean) {
    val request = OneTimeWorkRequestBuilder<SendJobWorker>()
        .setConstraints(buildConstraints(wifiOnly))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setInputData(Data.Builder().putString(KEY_JOB_ID, jobId).build())
        .build()
    enqueueUniqueWork(sendWorkName(jobId), ExistingWorkPolicy.KEEP, request)
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
    if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_TRANSFERS) == null) {
        manager.createNotificationChannel(
            NotificationChannel(NOTIFICATION_CHANNEL_TRANSFERS, "Transferts", NotificationManager.IMPORTANCE_LOW),
        )
    }
}

fun transferForegroundInfo(context: Context, notificationId: Int, title: String): ForegroundInfo {
    ensureTransferChannel(context)
    val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_TRANSFERS)
        .setContentTitle(title)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setOngoing(true)
        .build()
    return ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
}
