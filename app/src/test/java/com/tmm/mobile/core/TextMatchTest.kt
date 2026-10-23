package com.tmm.mobile.core

import java.text.Normalizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatchTest {
    @Test fun matchesQuery_ignoresUnicodeFormAndWidth() {
        val decomposed = Normalizer.normalize("スパイ教室", Normalizer.Form.NFD)
        assertTrue(matchesQuery(decomposed, "スパ"))
        assertTrue(matchesQuery("スパイ教室", "ｽﾊﾟ"))
        assertTrue(matchesQuery("Blue Lock", " blue "))
        assertFalse(matchesQuery("スパイ教室", "パン"))
    }

    @Test fun nfc_composesDecomposedNames() {
        assertTrue(nfc(Normalizer.normalize("らくがき パ", Normalizer.Form.NFD)) == "らくがき パ")
    }
}
