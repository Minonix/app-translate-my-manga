package com.tmm.mobile.mihon

import android.graphics.BitmapFactory

/**
 * Pages blanches (ou noires) en tete de chapitre : credits, page de garde vide. Prises pour couverture,
 * elles donnaient une vignette blanche. On les saute en choisissant la premiere page « pleine ».
 */
internal object BlankPage {
    /** Nombre de pages essayees en tete de chapitre avant de garder la premiere faute de mieux. */
    const val MAX_TRIED = 5

    private const val SAMPLE_SIDE = 64
    private const val WHITE = 235
    private const val BLACK = 25
    private const val BLANK_RATIO = 0.96

    /**
     * Vrai si au moins 96 % des pixels (ARGB) sont quasi blancs, ou quasi noirs. Une page de manga
     * ordinaire reste sous ce seuil (traits, trames) ; une page de credits ou vide le depasse.
     */
    fun isMostlyBlank(pixels: IntArray): Boolean {
        if (pixels.isEmpty()) return false
        var white = 0
        var black = 0
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = (r * 299 + g * 587 + b * 114) / 1000
            if (lum >= WHITE) white++ else if (lum <= BLACK) black++
        }
        val limit = pixels.size * BLANK_RATIO
        return white >= limit || black >= limit
    }

    /** Decode une vignette ~64 px : faux si l'image est illisible (format inconnu), on la garde alors. */
    fun isBlank(bytes: ByteArray): Boolean {
        val pixels = runCatching { samplePixels(bytes) }.getOrNull() ?: return false
        return isMostlyBlank(pixels)
    }

    private fun samplePixels(bytes: ByteArray): IntArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SAMPLE_SIDE) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return try {
            IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Premiere page non blanche parmi [pages] (dans l'ordre), sinon la premiere : une serie dont toutes
     * les pages testees sont blanches garde quand meme une couverture plutot que rien.
     */
    fun <T> pickCover(pages: Sequence<T>, bytesOf: (T) -> ByteArray, isBlank: (ByteArray) -> Boolean = ::isBlank): T? {
        var first: T? = null
        for (page in pages.take(MAX_TRIED)) {
            if (first == null) first = page
            if (!isBlank(bytesOf(page))) return page
        }
        return first
    }
}
