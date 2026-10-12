package com.tmm.mobile.data.status

import com.tmm.mobile.data.db.JobEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Ignore les emissions qui ne changent que la progression d'envoi (SendJobWorker ecrit
 * transferredBytes/transferTotal/updatedAt jusqu'a une fois par seconde et par worker). Sans ce
 * filtre, chaque tick relance les calculs lourds (jointure catalogue, badge, statuts) de tous les
 * ecrans abonnes. La liste emise est celle d'origine : un vrai changement apporte aussi les
 * valeurs de progression a jour.
 *
 * A ne pas utiliser la ou la progression ou updatedAt sont affiches (Transferts en cours, tri
 * des echecs de la File).
 */
fun Flow<List<JobEntity>>.ignoringProgress(): Flow<List<JobEntity>> =
    distinctUntilChanged { old, new -> old.size == new.size && old.indices.all { sameIgnoringProgress(old[it], new[it]) } }

private fun sameIgnoringProgress(a: JobEntity, b: JobEntity): Boolean =
    a === b || a == b.copy(transferredBytes = a.transferredBytes, transferTotal = a.transferTotal, updatedAt = a.updatedAt)
