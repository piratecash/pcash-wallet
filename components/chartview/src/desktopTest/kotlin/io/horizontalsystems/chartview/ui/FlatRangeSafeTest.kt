package io.horizontalsystems.chartview.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class FlatRangeSafeTest {

    @Test
    fun flatRangeSafe_distinctRange_unchanged() {
        assertEquals(1f to 5f, flatRangeSafe(1f, 5f))
    }

    @Test
    fun flatRangeSafe_equalNonZero_lowersMin() {
        assertEquals(1.8f to 2f, flatRangeSafe(2f, 2f))
    }

    @Test
    fun flatRangeSafe_allZero_returnsUnitRange() {
        assertEquals(0f to 1f, flatRangeSafe(0f, 0f))
    }
}
