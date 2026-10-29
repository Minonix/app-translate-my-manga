package com.tmm.mobile.workers

import com.tmm.mobile.core.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * Verrou de processus des installations, a limite reglable (AppPrefs.maxConcurrentInstalls).
 *
 * Chaque chapitre a son propre travail unique : apres une nuit de traductions, des dizaines de resultats
 * arrivent ensemble et WorkManager les lancait tous en parallele (telechargement, client OkHttp de
 * couverture, couverture de 10 Mo en memoire, liste SAF par job) -> OutOfMemoryError. Les installations
 * passent donc par ce compteur, quelques-unes a la fois ; les autres attendent suspendues (sans thread
 * bloque) et la file se vide par lots.
 *
 * Compteur maison plutot qu'un Semaphore : la limite est relue a chaque attente, une hausse libere tout
 * de suite les travaux en attente et une baisse s'applique aux suivants sans couper ceux en cours.
 */
object InstallSlots {
    private val running = MutableStateFlow(0)
    // Jobs dont le worker tourne mais attend une place : leur updatedAt est vieux sans qu'ils soient figes
    // (InstallRecovery ne doit pas les relancer).
    // Compteur par job et non simple ensemble : lors d'un REPLACE, l'ancien worker annule ne doit pas
    // effacer la marque du nouveau, deja en attente.
    private val waiting = ConcurrentHashMap<String, Int>()

    val runningCount: Int get() = running.value

    fun isWaiting(jobId: String): Boolean = (waiting[jobId] ?: 0) > 0

    suspend fun <T> withSlot(jobId: String, limit: Flow<Int>, block: suspend () -> T): T {
        waiting.merge(jobId, 1) { a, b -> a + b }
        try {
            acquire(limit)
        } finally {
            waiting.computeIfPresent(jobId) { _, n -> (n - 1).takeIf { it > 0 } }
        }
        try {
            return block()
        } finally {
            running.update { it - 1 }
        }
    }

    private suspend fun acquire(limit: Flow<Int>) {
        while (true) {
            // Attend une place libre sous la limite courante, puis la prend si personne ne l'a prise entre-temps.
            val max = combine(running, limit) { count, cap -> count to cap }.first { (count, cap) -> count < cap }.second
            val current = running.value
            if (current < max && running.compareAndSet(current, current + 1)) return
        }
    }
}

/**
 * Inactivite d'un telechargement, mesuree sur les evenements de progression (octets recus) avec l'horloge
 * injectee : un transfert lent qui progresse n'est jamais considere inactif, quelle que soit sa duree.
 */
class IdleWatch(private val clock: Clock, private val limitMillis: Long) {
    @Volatile private var lastReceived = -1L
    @Volatile private var lastProgressAt = clock.nowMillis()

    fun onProgress(received: Long) {
        if (received == lastReceived) return
        lastReceived = received
        lastProgressAt = clock.nowMillis()
    }

    fun isIdle(): Boolean = clock.nowMillis() - lastProgressAt >= limitMillis
}
