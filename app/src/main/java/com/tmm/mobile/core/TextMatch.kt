package com.tmm.mobile.core

import java.text.Normalizer

// Un meme titre japonais peut arriver sous deux formes Unicode : « パ » d'un bloc (NFC, clavier, Mihon) ou
// « ハ » + « ゜ » (NFD, noms de fichiers rendus par certains stockages), voire en demi-chasse (« ﾊﾟ »).
// Identiques a l'ecran, differents pour String.equals/contains : la serie n'etait ni rattachee ni trouvee.

/** Forme canonique d'un nom de fichier/dossier (NFC) : a comparer avec ==. */
fun nfc(text: String): String =
    if (Normalizer.isNormalized(text, Normalizer.Form.NFC)) text else Normalizer.normalize(text, Normalizer.Form.NFC)

/** Cle de recherche : NFKC (demi-chasse, ligatures) puis minuscules. */
fun searchKey(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()

/** Vrai si `query` apparait dans `text`, quelles que soient la casse et la forme Unicode. */
fun matchesQuery(text: String, query: String): Boolean = searchKey(text).contains(searchKey(query.trim()))
