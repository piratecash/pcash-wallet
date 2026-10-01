package cash.p.terminal.core.deeplink

import cash.p.terminal.R
import cash.p.terminal.core.ICoinManager
import cash.p.terminal.modules.main.DeeplinkPage
import cash.p.terminal.modules.multiswap.SwapDeeplinkInput
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeeplinkParserTest {

    private val accountManager = mockk<IAccountManager>()
    private val coinManager = mockk<ICoinManager> { every { getToken(any()) } returns null }
    private val parser = DeeplinkParser(coinManager, mockk(relaxed = true), accountManager)

    @Test
    fun parse_swapWithWatchActiveAccount_returnsNull() {
        val watchTypes = listOf(
            AccountType.ThorchainAddress("thor1watched"),
            AccountType.StellarAddress("GWATCHED"),
        )

        for (type in watchTypes) {
            every { accountManager.activeAccount } returns account(type)

            assertNull(parser.parse("pcash://swap?to_token=PIRATE"))
        }
        verify(exactly = 0) { coinManager.getToken(any()) }
    }

    @Test
    fun parse_swapWithNoActiveAccount_returnsNull() {
        every { accountManager.activeAccount } returns null

        assertNull(parser.parse("pcash://swap"))
    }

    @Test
    fun parse_swapWithBackedUpMnemonicAccount_returnsSwapPage() {
        every { accountManager.activeAccount } returns account(AccountType.Mnemonic(List(12) { "abandon" }, ""))

        assertEquals(DeeplinkPage(R.id.multiswap, SwapDeeplinkInput(null)), parser.parse("pcash://swap"))
    }

    @Test
    fun parse_premiumWithWatchActiveAccount_returnsPremiumPage() {
        every { accountManager.activeAccount } returns account(AccountType.ThorchainAddress("thor1watched"))

        assertEquals(DeeplinkPage(R.id.aboutPremiumFragment, null), parser.parse("pcash://premium"))
    }

    private fun account(type: AccountType) = Account(
        id = "account-id",
        name = "Account",
        type = type,
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = true,
    )
}
