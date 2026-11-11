package cash.p.terminal.modules.nft.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NftCardsRowSnapTest {

    private val size = 200
    private val viewportEnd = 1000
    private val padding = 100

    private fun offsetFor(itemOffset: Int) = showAllSnapOffset(itemOffset, size, viewportEnd, padding)

    @Test
    fun showAllSnapOffset_cellFullyVisible_noScroll() {
        assertEquals(0f, offsetFor(itemOffset = 700))
    }

    @Test
    fun showAllSnapOffset_moreThanHalfVisible_scrollsToRevealIt() {
        assertEquals(150f, offsetFor(itemOffset = 850))
    }

    @Test
    fun showAllSnapOffset_exactlyHalfVisible_revealsIt() {
        assertEquals(200f, offsetFor(itemOffset = 900))
    }

    @Test
    fun showAllSnapOffset_lessThanHalfVisible_scrollsBackToHideIt() {
        assertEquals(-50f, offsetFor(itemOffset = 950))
    }

    @Test
    fun showAllSnapOffset_cellNotVisible_noScroll() {
        assertEquals(0f, offsetFor(itemOffset = 1000))
        assertEquals(0f, offsetFor(itemOffset = 1300))
    }
}
