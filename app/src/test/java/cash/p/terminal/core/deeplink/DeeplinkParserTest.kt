package cash.p.terminal.core.deeplink

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.feature.miniapp.ui.connect.ConnectMiniAppDeeplinkInput
import cash.p.terminal.feature.miniapp.ui.connect.ConnectMiniAppPage
import cash.p.terminal.modules.multiswap.SwapPage
import cash.p.terminal.modules.premium.about.AboutPremiumPage
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DeeplinkParserTest {

    private val accountManager = mockk<IAccountManager>()
    private val coinManager = mockk<ICoinManager> { every { getToken(any()) } returns null }
    private val parser = DeeplinkParser(coinManager, mockk(relaxed = true), accountManager)

    @Test
    fun parse_premium_opensAboutPremiumPageFromRight() {
        val deeplinkPage = parser.parse("pcash://premium")

        assertEquals(false, deeplinkPage?.fromBottom)
        assertNull(assertIs<AboutPremiumPage>(deeplinkPage?.page).input)
    }

    @Test
    fun parse_premiumWithWatchActiveAccount_returnsPremiumPage() {
        every { accountManager.activeAccount } returns account(AccountType.ThorchainAddress("thor1watched"))

        assertIs<AboutPremiumPage>(parser.parse("pcash://premium")?.page)
    }

    @Test
    fun parse_swapToPirate_opensSwapPageWithTokenOutFromRight() {
        every { accountManager.activeAccount } returns account(AccountType.Mnemonic(List(12) { "abandon" }, ""))
        val token = mockk<Token>()
        every { coinManager.getToken(TokenQuery.PirateCashBnb) } returns token

        val deeplinkPage = parser.parse("pcash://swap?to_token=pirate")

        assertEquals(false, deeplinkPage?.fromBottom)
        assertSame(token, assertIs<SwapPage>(deeplinkPage?.page).tokenOut)
    }

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
    fun parse_auth_opensConnectMiniAppPageFromBottom() {
        val deeplinkPage = parser.parse("pcash://auth?token=jwt&env=stage")

        assertEquals(true, deeplinkPage?.fromBottom)
        assertEquals(
            ConnectMiniAppDeeplinkInput(jwt = "jwt", endpoint = "https://anubis.pirate.place/"),
            assertIs<ConnectMiniAppPage>(deeplinkPage?.page).input,
        )
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
