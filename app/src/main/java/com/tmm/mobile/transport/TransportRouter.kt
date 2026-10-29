package com.tmm.mobile.transport

import com.tmm.mobile.data.db.CHANNEL_DIRECT
import com.tmm.mobile.data.db.CHANNEL_DRIVE
import com.tmm.mobile.data.db.CHANNEL_PC_DIRECT
import com.tmm.mobile.data.db.JobEntity

// Choix du canal : un job reste sur le canal decide a l'envoi jusqu'a son resultat (forJob) ;
// les operations sans job (nouveau job, reglages) prennent le canal actif : Hub VPS, puis PC en
// direct, puis Drive.
class TransportRouter(val drive: JobTransport, val hub: DirectTransport, val pc: DirectTransport) {

    fun forChannel(channel: String): JobTransport = when (channel) {
        CHANNEL_DIRECT -> hub
        CHANNEL_PC_DIRECT -> pc
        else -> drive
    }

    fun forJob(job: JobEntity): JobTransport = forChannel(job.channel)

    // Chaque emplacement n'est essaye que s'il est associe (lecture locale, aucun appel reseau) puis
    // repond au health-check (3 s). Hub et PC tous deux associes et muets : jusqu'a 6 s avant Drive.
    // ponytail: health-checks en serie, l'appel OkHttp bloquant ne s'annule pas proprement en parallele ;
    // a paralleliser (Call.enqueue + cancel) si ces 6 s se font sentir.
    suspend fun activeChannel(): String = when {
        hub.isPaired() && hub.health() != null -> CHANNEL_DIRECT
        pc.isPaired() && pc.health() != null -> CHANNEL_PC_DIRECT
        else -> CHANNEL_DRIVE
    }

    suspend fun active(): JobTransport = forChannel(activeChannel())
}
