package com.tmm.mobile.mihon

import com.tmm.mobile.core.nfc
import com.tmm.mobile.data.db.LocalFrChapterEntity
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

// Traductions FR de local/ reconnues par (dossier de serie, numero), sans mihonPath : les FR traduites
// depuis des originaux du PC (jamais envoyes par le telephone) n'en ont pas, et leurs chapitres
// restaient « A traduire » ou « Pas telecharge » alors que la FR etait lisible dans Mihon.

// Nom des fichiers poses par le PC dans local/ : "c0033 - Ch. 33.cbz", "c0014.1 - Ch. 14.1.cbz".
private val LOCAL_CHAPTER_NUMBER = Regex("""^c(\d+(?:\.\d+)?)""")

/** Numero d'un chapitre FR de local/ ("c0061 - Chapter 61.cbz" -> 61), null pour tout autre fichier. */
fun localFrChapterNumber(fileName: String): Float? {
    if (!fileName.endsWith(".cbz", ignoreCase = true)) return null
    return LOCAL_CHAPTER_NUMBER.find(fileName)?.groupValues?.get(1)?.toFloatOrNull()
}

/**
 * Cle de comparaison d'un nom de dossier serie (local/, downloads/) ou d'un titre : NFC, minuscules,
 * lettres et chiffres seuls. "Titre: Sous-titre" (titre de la sauvegarde), "Titre_ Sous-titre" (dossier
 * Mihon) et "titre sous titre" donnent la meme cle. NFC d'abord : un accent decompose (NFD) serait
 * sinon retire alors que sa forme composee est gardee.
 */
fun localFolderKey(name: String): String = nfc(name).lowercase().filter { it.isLetterOrDigit() }

/**
 * Numeros FR presents par dossier de local/, cle [localFolderKey]. [files] = (nom du dossier, nom du
 * fichier). Reutilisable pour un dossier local/ choisi par l'utilisateur : lui passer ses fichiers.
 */
fun localFrNumbersByFolder(files: Iterable<Pair<String, String>>): Map<String, Set<Float>> {
    val byFolder = HashMap<String, MutableSet<Float>>()
    for ((folder, file) in files) {
        val number = localFrChapterNumber(file) ?: continue
        byFolder.getOrPut(localFolderKey(folder)) { HashSet() } += number
    }
    return byFolder
}

/**
 * Vrai si le chapitre [number] est couvert par l'une des FR [frNumbers] d'une serie.
 * Regle (choisie pour ne jamais masquer un chapitre non traduit) :
 * - meme numero : couvert ;
 * - FR entiere N, chapitre N.1 a N.4 : couvert. Une scantrad qui decoupe le 61 en 61.1 / 61.2 face a
 *   une FR traduite depuis l'edition entiere 61 (Frontier Lord : KaynScan contre Official) ;
 * - FR partielle N.x : ne couvre qu'elle-meme (ni N, ni N.y).
 * ponytail: N.5 et au-dela ne sont jamais couverts par N, car Mihon numerote ainsi les hors-series
 * (7.5) ; un decoupage en 5 parties ou plus reste donc « A traduire » a partir de la cinquieme.
 * Volontairement sans contexte (autres numeros de la serie) : la jointure catalogue et les ecrans qui
 * ne voient que les telechargements donnent ainsi le meme statut.
 */
fun localFrCovers(frNumbers: Set<Float>, number: Float): Boolean {
    if (number < 0f) return false
    if (number in frNumbers) return true
    val whole = floor(number)
    if (whole == number || whole !in frNumbers) return false
    val tenths = number * 10f
    val rounded = tenths.roundToInt()
    return abs(tenths - rounded) < 1e-3f && rounded % 10 in 1..4
}

/** Index des FR de local/ par dossier, construit une fois par instantane de la base. */
class LocalFrNumbers(private val byFolder: Map<String, Set<Float>>) {
    /** Vrai si l'une des [folderNames] (titre de la serie, dossier downloads/) a une FR couvrant [number]. */
    fun covers(number: Float?, vararg folderNames: String?): Boolean {
        if (number == null || byFolder.isEmpty()) return false
        return folderNames.asSequence().filterNotNull().map(::localFolderKey).distinct()
            .any { key -> byFolder[key]?.let { localFrCovers(it, number) } == true }
    }

    companion object {
        fun of(localFr: Iterable<LocalFrChapterEntity>): LocalFrNumbers =
            LocalFrNumbers(localFrNumbersByFolder(localFr.map { it.folderName to it.fileName }))
    }
}
