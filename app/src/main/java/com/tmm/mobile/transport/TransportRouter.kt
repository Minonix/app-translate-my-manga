package com.tmm.mobile.transport

import com.tmm.mobile.data.db.CHANNEL_DIRECT
import com.tmm.mobile.data.db.CHANNEL_DRIVE
import com.tmm.mobile.data.db.JobEntity

// Choix du canal : un job reste sur le canal decide a l'envoi jusqu'a son resultat (forJob) ;
// les operations sans job (nouveau job, reglages, poll) prennent le canal actif, direct si le PC repond.
class TransportRouter(val drive: JobTransport, val direct: DirectTransport) {

    fun forChannel(channel: String): JobTransport = if (channel == CHANNEL_DIRECT) direct else drive

    fun forJob(job: JobEntity): JobTransport = forChannel(job.channel)

    // Direct seulement si un PC est associe ET repond au health-check (6 s) ; sinon Drive, en silence.
    suspend fun activeChannel(): String =
        if (direct.isPaired() && direct.health() != null) CHANNEL_DIRECT else CHANNEL_DRIVE

    suspend fun active(): JobTransport = forChannel(activeChannel())
}
