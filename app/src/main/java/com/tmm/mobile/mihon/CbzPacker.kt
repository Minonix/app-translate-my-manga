package com.tmm.mobile.mihon

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// PLAN_GEMINI.md 5.14 : CBZ en zip STORED uniquement, tri en ordre naturel, CRC32/taille
// calcules avant ecriture, pageCount = nombre d'entrees image (insensible a la casse),
// dossiers/fichiers caches et temporaires Mihon exclus.
object CbzPacker {
    // "heif" : extension que Mihon donne aux pages HEIF ; sans elle, un chapitre HEIF restait « Pas telecharge ».
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "jxl", "heif", "heic")

    fun isImage(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    fun isEligible(name: String): Boolean {
        val base = name.substringAfterLast('/')
        if (base.startsWith(".")) return false
        if (base.endsWith(".tmp")) return false
        return true
    }

    fun pageCount(names: List<String>): Int = names.count { isEligible(it) && isImage(it) }

    /** Comparateur d'ordre naturel : les suites de chiffres sont comparees numeriquement. */
    val NATURAL_ORDER: Comparator<String> = Comparator { a, b ->
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                var iEnd = i
                while (iEnd < a.length && a[iEnd].isDigit()) iEnd++
                var jEnd = j
                while (jEnd < b.length && b[jEnd].isDigit()) jEnd++
                val numA = a.substring(i, iEnd).trimStart('0').ifEmpty { "0" }
                val numB = b.substring(j, jEnd).trimStart('0').ifEmpty { "0" }
                val cmp = if (numA.length != numB.length) numA.length - numB.length else numA.compareTo(numB)
                if (cmp != 0) return@Comparator cmp
                i = iEnd
                j = jEnd
            } else {
                if (ca != cb) return@Comparator ca.compareTo(cb)
                i++
                j++
            }
        }
        (a.length - i) - (b.length - j)
    }

    /** Empaquette [entries] (nom -> contenu) dans [output], tri en ordre naturel, exclusions appliquees. */
    fun pack(entries: Map<String, ByteArray>, output: OutputStream) = pack(entries.keys, entries::getValue, output)

    /**
     * Meme chose, mais une page lue a la fois via [read] : un chapitre entier ne tient jamais en memoire
     * (plusieurs envois simultanes de chapitres de 50 Mo faisaient planter l'app).
     */
    fun pack(names: Collection<String>, read: (String) -> ByteArray, output: OutputStream) {
        val sorted = names.filter(::isEligible).sortedWith(NATURAL_ORDER)
        ZipOutputStream(output).use { zos ->
            zos.setMethod(ZipOutputStream.STORED)
            for (name in sorted) {
                val bytes = read(name)
                val entry = ZipEntry(name).apply {
                    size = bytes.size.toLong()
                    compressedSize = bytes.size.toLong()
                    crc = CRC32().apply { update(bytes) }.value
                }
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
    }

    fun copyExisting(input: InputStream, output: OutputStream) {
        input.copyTo(output)
    }
}
