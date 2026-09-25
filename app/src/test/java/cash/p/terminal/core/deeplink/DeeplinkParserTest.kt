package cash.p.terminal.core.deeplink

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.feature.miniapp.ui.connect.ConnectMiniAppDeeplinkInput
import cash.p.terminal.feature.miniapp.ui.connect.ConnectMiniAppPage
import cash.p.terminal.modules.multiswap.SwapPage
import cash.p.terminal.modules.premium.about.AboutPremiumPage
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.TokenQuery
import io.mockk.every
import io.mockk.mockk
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

    private val coinManager = mockk<ICoinManager>()
    private val parser = DeeplinkParser(coinManager, mockk(relaxed = true))

    @Test
    fun parse_premium_opensAboutPremiumPageFromRight() {
        val deeplinkPage = parser.parse("pcash://premium")

        assertEquals(false, deeplinkPage?.fromBottom)
        assertNull(assertIs<AboutPremiumPage>(deeplinkPage?.page).input)
    }

    @Test
    fun parse_swapToPirate_opensSwapPageWithTokenOutFromRight() {
        val token = mockk<Token>()
        every { coinManager.getToken(TokenQuery.PirateCashBnb) } returns token

        val deeplinkPage = parser.parse("pcash://swap?to_token=pirate")

        assertEquals(false, deeplinkPage?.fromBottom)
        assertSame(token, assertIs<SwapPage>(deeplinkPage?.page).tokenOut)
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
}
