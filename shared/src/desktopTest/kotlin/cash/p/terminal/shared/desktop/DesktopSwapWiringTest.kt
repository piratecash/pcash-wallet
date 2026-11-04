package cash.p.terminal.shared.desktop

import cash.p.terminal.modules.multiswap.SwapPage
import cash.p.terminal.modules.multiswap.exchanges.MultiSwapExchangesPage
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.policy.PlatformOperation
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSwapWiringTest {

    private val pages = DesktopWalletPages(mockk(relaxed = true))
    private val token = mockk<Token>()

    @Test
    fun supports_swap_returnsTrue() {
        assertTrue(DesktopPlatformOperationSupport.supports(PlatformOperation.Swap))
    }

    @Test
    fun swap_tokenOut_opensSwapPageBuyingIt() {
        val page = assertIs<SwapPage>(pages.swap(token))

        assertNull(page.tokenIn)
        assertEquals(token, page.tokenOut)
    }

    @Test
    fun swapFrom_tokenIn_opensSwapPageSellingIt() {
        val page = assertIs<SwapPage>(pages.swapFrom(token))

        assertEquals(token, page.tokenIn)
        assertNull(page.tokenOut)
    }

    @Test
    fun multiSwapExchanges_pendingSwap_opensExchangesPageForIt() {
        val page = assertIs<MultiSwapExchangesPage>(pages.multiSwapExchanges("swap-id"))

        assertEquals("swap-id", page.pendingMultiSwapId)
    }
}
