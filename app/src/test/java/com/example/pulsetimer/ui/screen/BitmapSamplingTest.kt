package com.pulsetimer.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapSamplingTest {
    @Test
    fun keepsImagesAtOrBelowRequestedSizeUnscaled() {
        assertEquals(1, calculateBitmapSampleSize(1080, 720, 1080))
        assertEquals(1, calculateBitmapSampleSize(640, 480, 1080))
    }

    @Test
    fun selectsPowerOfTwoSampleThatFitsRequestedSize() {
        assertEquals(2, calculateBitmapSampleSize(1920, 1080, 1080))
        assertEquals(4, calculateBitmapSampleSize(4032, 3024, 1080))
        assertEquals(4, calculateBitmapSampleSize(2200, 1600, 1080))
    }

    @Test
    fun invalidDimensionsUseUnscaledFallback() {
        assertEquals(1, calculateBitmapSampleSize(0, 720, 1080))
        assertEquals(1, calculateBitmapSampleSize(720, -1, 1080))
        assertEquals(1, calculateBitmapSampleSize(720, 480, 0))
    }
}
