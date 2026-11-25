package com.tmm.mobile.data

import com.tmm.mobile.core.nfc
import com.tmm.mobile.data.db.SeriesLinkEntity
import com.tmm.mobile.mihon.DownloadOnlySeries
import com.tmm.mobile.mihon.localFolderKey

// Liaison manuelle d'une serie a un dossier de local/ : une meme oeuvre suivie sur deux sources a deux
// titres (« See You at the Pepper Field! » sur AllManga, « See You in the Chili Pepper Field! » sur Comix).
// Jamais de fusion floue automatique : l'utilisateur choisit le dossier, les suggestions ne font que l'aider.

/**
 * Cle de stockage de la liaison d'une serie. Le couple de dossiers downloads/ d'abord : une serie hors
 * bibliotheque ("downloads|src|dir") change de seriesKey en entrant en bibliotheque, pas de dossier.
 * Sans dossier downloads/, la seriesKey. [dirs] = (sourceDir, seriesDir) des chapitres telecharges.
 */
fun seriesLinkOwnerKey(seriesKey: String, dirs: Collection<Pair<String, String>>): String {
    if (DownloadOnlySeries.isDownloadOnly(seriesKey)) return seriesKey
    val dir = dirs.minWithOrNull(compareBy({ it.first }, { it.second })) ?: return seriesKey
    return DownloadOnlySeries.key(dir.first, dir.second)
}

/** Toutes les cles sous lesquelles la liaison de cette serie a pu etre posee (pour la retirer). */
fun seriesLinkOwnerKeys(seriesKey: String, dirs: Collection<Pair<String, String>>): List<String> =
    (dirs.map { (source, dir) -> DownloadOnlySeries.key(source, dir) } + seriesKey).distinct()

/** Index des liaisons, construit une fois par instantane de la base. */
class SeriesLinks(links: Iterable<SeriesLinkEntity>) {
    private val byOwner = links.associate { it.ownerKey to it.localFolder }

    /** Dossier lie d'un chapitre telecharge (ou d'un job) : couple de dossiers, puis seriesKey. */
    fun folderFor(seriesKey: String?, sourceDir: String?, seriesDir: String?): String? {
        if (byOwner.isEmpty()) return null
        if (sourceDir != null && seriesDir != null) byOwner[DownloadOnlySeries.key(sourceDir, seriesDir)]?.let { return it }
        return seriesKey?.let { byOwner[it] }
    }

    /** Dossier lie d'une serie, d'apres ses dossiers downloads/ [dirs] puis sa seriesKey. */
    fun folderForSeries(seriesKey: String, dirs: Iterable<Pair<String, String>>): String? {
        if (byOwner.isEmpty()) return null
        for ((source, dir) in dirs) byOwner[DownloadOnlySeries.key(source, dir)]?.let { return it }
        return byOwner[seriesKey]
    }

    companion object {
        val EMPTY = SeriesLinks(emptyList())
    }
}

// Mots trop courants pour rapprocher deux titres ("the", "de"...).
private val STOP_WORDS = setOf(
    "a", "an", "the", "of", "at", "in", "on", "to", "and", "is",
    "le", "la", "les", "l", "de", "du", "des", "d", "un", "une", "et", "au", "aux",
)

private fun titleWords(name: String): Set<String> =
    nfc(name).lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() && it !in STOP_WORDS }.toSet()

/**
 * Dossiers de [candidates] au titre proche de [title] (mots communs, indice de Jaccard), du plus proche
 * au moins proche. Simple aide a la saisie : rien n'est lie sans un clic.
 * ponytail: mots exacts seulement (pas de racine ni de traduction) ; « Chili » ou un titre traduit ne
 * rapprochent rien, la liste complete et la recherche restent la pour ces cas.
 */
fun seriesLinkSuggestions(title: String, candidates: List<String>, limit: Int = 3): List<String> {
    val words = titleWords(title)
    if (words.isEmpty()) return emptyList()
    val ownKey = localFolderKey(title)
    return candidates.asSequence()
        .filter { localFolderKey(it) != ownKey }
        .map { candidate ->
            val other = titleWords(candidate)
            val common = words.count { it in other }
            val union = (words + other).size
            candidate to if (union == 0) 0.0 else common.toDouble() / union
        }
        .filter { (_, score) -> score >= 0.4 }
        .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first.lowercase() })
        .take(limit)
        .map { it.first }
        .toList()
}

/**
 * Cibles proposees : dossiers de local/ existants puis titres des series connues (dossier que le PC
 * creerait, [folderOfTitle]), dedoublonnes par [localFolderKey] en gardant le dossier existant, sans les
 * noms de la serie elle-meme ([ownNames] : titre, dossier downloads/).
 */
fun seriesLinkCandidates(
    localFolders: Collection<String>,
    seriesTitles: Collection<String>,
    ownNames: Collection<String>,
    folderOfTitle: (String) -> String,
): List<String> {
    val own = ownNames.mapTo(HashSet(), ::localFolderKey)
    val seen = HashSet<String>()
    val out = ArrayList<String>()
    for (name in localFolders.sortedBy { it.lowercase() } + seriesTitles.map(folderOfTitle).sortedBy { it.lowercase() }) {
        val key = localFolderKey(name)
        if (key.isEmpty() || key in own || !seen.add(key)) continue
        out += name
    }
    return out.sortedBy { it.lowercase() }
}
