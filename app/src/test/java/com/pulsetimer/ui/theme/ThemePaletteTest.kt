package com.pulsetimer.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePaletteTest {
    @Test
    fun acceptsSixAndEightDigitHexColors() {
        assertTrue(isValidPaletteColor("#9BB9A8"))
        assertTrue(isValidPaletteColor("#FF9BB9A8"))
    }

    @Test
    fun rejectsIncompleteAndMalformedHexColors() {
        assertFalse(isValidPaletteColor(""))
        assertFalse(isValidPaletteColor("#"))
        assertFalse(isValidPaletteColor("#12"))
        assertFalse(isValidPaletteColor("#GGGGGG"))
        assertFalse(isValidPaletteColor("9BB9A8"))
        assertFalse(isValidPaletteColor("#123456789"))
    }
}
