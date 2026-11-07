package cash.p.terminal.shared.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.SolanaRpcSourceManager
import cash.p.terminal.entities.BtcRestoreMode
import cash.p.terminal.modules.btcblockchainsettings.BtcBlockchainSettingsPage
import cash.p.terminal.modules.solananetwork.SolanaNetworkPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.shared.DesktopTestGraph
import cash.p.terminal.shared.PcashApp
import cash.p.terminal.shared.TestWindowHost
import cash.p.terminal.shared.main.DesktopMainPage
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertSame

@OptIn(ExperimentalTestApi::class)
class StateDrivenCloseDesktopTest {

    private class ParentPage : HSPage() {
        @Composable
        override fun GetContent(navigation: HSNavigation) {
            Text("parent page")
        }
    }

    private val parent = ParentPage()

    @BeforeTest
    fun setUp() {
        DesktopTestGraph.start()
        loadKoinModules(
            module {
                single<BtcBlockchainManager> {
                    mockk(relaxed = true) {
                        every { restoreMode(any()) } returns BtcRestoreMode.Blockchair
                        every { availableRestoreModes(any()) } returns BtcRestoreMode.entries
                    }
                }
                single { SolanaRpcSourceManager(mockk(relaxed = true), mockk(relaxed = true)) }
            }
        )
    }

    @AfterTest
    fun tearDown() = DesktopTestGraph.stop()

    @Test
    fun btcBlockchainSettings_save_closesOnlyItself() = runDesktopComposeUiTest {
        val navigation = showPageOverParent(BtcBlockchainSettingsPage(Blockchain(BlockchainType.Bitcoin, "Bitcoin", null)))

        onNodeWithText("Hybrid").performClick()
        onNodeWithText("Save").performClick()
        mainClock.advanceTimeBy(EXIT_ANIMATION_MS)

        assertSame(parent, navigation.lastOrNull())
    }

    @Test
    fun solanaNetwork_select_closesOnlyItself() = runDesktopComposeUiTest {
        val navigation = showPageOverParent(SolanaNetworkPage())

        onNodeWithText("TritonOne API").performClick()
        mainClock.advanceTimeBy(EXIT_ANIMATION_MS)

        assertSame(parent, navigation.lastOrNull())
    }

    private fun ComposeUiTest.showPageOverParent(page: HSPage): HSNavigation {
        val navigation = HSNavigation(NavBackStack<HSPage>(DesktopMainPage(), parent, page))
        setContent { TestWindowHost { ComposeAppTheme { PcashApp(remember { navigation }) } } }
        return navigation
    }

    private companion object {
        const val EXIT_ANIMATION_MS = 2_000L
    }
}
