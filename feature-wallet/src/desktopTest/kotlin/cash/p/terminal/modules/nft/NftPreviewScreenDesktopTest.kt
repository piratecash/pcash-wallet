package cash.p.terminal.modules.nft

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.core.nft.NftLinkButton
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.modules.nft.ui.previewNftItem
import cash.p.terminal.ui_compose.components.HudMessenger
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.WalletFactory
import io.mockk.mockk
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NftPreviewScreenDesktopTest {

    private val claim = NftLinkButton("Claim reward", "https://example.com/claim")
    private val market = NftLinkButton("Open market", "https://example.com/market")
    private val hud = mockk<HudMessenger>(relaxed = true)
    private val urlOpener = object : UrlOpener {
        override fun open(url: String) = Unit
    }
    @BeforeTest
    fun setUp() {
        startKoin { modules(module { single<IClipboardManager> { mockk(relaxed = true) } }) }
    }

    @AfterTest
    fun tearDown() = stopKoin()

    private val available get() = NftTransferAvailability.Available(WalletFactory.previewWallet(), null)

    private fun showScreen(
        availability: NftTransferAvailability,
        hidden: Boolean = false,
        onLinkClick: (NftLinkButton) -> Unit = {},
        assertions: ComposeUiTest.() -> Unit,
    ) = runDesktopComposeUiTest {
        setContent {
            ComposeAppTheme {
                CompositionLocalProvider(LocalUrlOpener provides urlOpener, LocalHudMessenger provides hud) {
                    NftPreviewScreen(
                        item = previewNftItem("EQBx"),
                        uiState = NftPreviewUiState(
                            hidden = hidden,
                            canReveal = hidden,
                            transferAvailability = availability,
                            trust = NftTrust.None,
                            linkButtons = listOf(claim, market),
                        ),
                        network = NftNetworkInfo(title = "TON", explorerUrl = null),
                        actions = NftPreviewActions(
                            onArtworkClick = {},
                            onSend = {},
                            onRenew = {},
                            onMenu = {},
                            onReportSpam = {},
                            onNotSpam = {},
                            onLinkClick = onLinkClick,
                            onBack = {},
                        ),
                    )
                }
            }
        }
        assertions()
    }

    @Test
    fun linkButtons_sendAvailable_clickDeliversThatButton() {
        val clicked = mutableListOf<NftLinkButton>()
        showScreen(available, onLinkClick = clicked::add) {
            onNodeWithText(claim.label).performScrollTo().assertIsEnabled()
            onNodeWithText(market.label).performScrollTo().performClick()
        }
        assertEquals(listOf(market), clicked)
    }

    @Test
    fun linkButtons_onSale_shownDisabled() =
        showScreen(NftTransferAvailability.Unavailable(NftTransferUnavailableReason.OnSale)) {
            onNodeWithText(claim.label).performScrollTo().assertIsNotEnabled()
        }

    @Test
    fun linkButtons_noTonConnect_notShown() = showScreen(NftTransferAvailability.Hidden) {
        onNodeWithText(claim.label).assertDoesNotExist()
    }

    @Test
    fun linkButtons_balanceHidden_notShown() = showScreen(available, hidden = true) {
        onNodeWithText(claim.label).assertDoesNotExist()
    }
}
