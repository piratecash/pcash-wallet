package cash.p.terminal.shared.desktop.menu

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.modules.manageaccount.ManageAccountPage
import cash.p.terminal.modules.managewallets.ManageWalletsPage
import cash.p.terminal.modules.settings.about.AboutPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.shared.DesktopTestGraph
import cash.p.terminal.shared.desktop.session.mnemonicAccount
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DesktopVaultState
import cash.p.terminal.shared.main.DesktopMainPage
import cash.p.terminal.shared.main.MainDestination
import cash.p.terminal.ui_compose.ModalOverlayTracker
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class DesktopMenuDispatchDesktopTest {

    private val mainPage = DesktopMainPage()
    private val navigation = HSNavigation(NavBackStack<HSPage>(mainPage))
    private val account: Account = mnemonicAccount(isBackedUp = true)

    @BeforeTest
    fun setUp() {
        DesktopTestGraph.startUnlocked()
    }

    @AfterTest
    fun tearDown() {
        DesktopTestGraph.stop()
    }

    @Test
    fun execute_coinManagerOnMainPage_opensCoinManager() {
        actions().execute(DesktopMenuCommand.CoinManager)

        assertIs<ManageWalletsPage>(navigation.backStack.last())
    }

    @Test
    fun execute_coinManagerWithPageOnTop_ignored() {
        navigation.slideFromRight(AboutPage())

        actions().execute(DesktopMenuCommand.CoinManager)

        assertIs<AboutPage>(navigation.backStack.last())
        assertEquals(2, navigation.backStack.size)
    }

    @Test
    fun execute_coinManagerUnderModalSheet_ignored() = runDesktopComposeUiTest {
        var sheetOpen by mutableStateOf(true)
        setContent {
            ComposeAppTheme {
                if (sheetOpen) {
                    TransparentModalBottomSheet(
                        onDismissRequest = { sheetOpen = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    ) { Text("Sheet") }
                }
            }
        }
        waitForIdle()

        runOnIdle { actions().execute(DesktopMenuCommand.CoinManager) }

        assertEquals(listOf<HSPage>(mainPage), navigation.backStack.toList())
        // The tracker is process-wide: let its dismiss grace run out before the next test.
        sheetOpen = false
        waitUntil(timeoutMillis = 5_000) { !ModalOverlayTracker.hasForegroundModal }
    }

    @Test
    fun execute_showSettings_selectsTheSettingsTab() {
        actions().execute(DesktopMenuCommand.ShowSettings)

        assertEquals(MainDestination.Settings, mainPage.selectedTab)
        assertEquals(1, navigation.backStack.size)
    }

    @Test
    fun execute_walletSettings_opensTheActiveAccount() {
        actions().execute(DesktopMenuCommand.WalletSettings)

        val page = assertIs<ManageAccountPage>(navigation.backStack.last())
        assertEquals(account.id, page.input.accountId)
    }

    @Test
    fun execute_lock_locksTheVault() {
        runBlocking { actions(scope = this).execute(DesktopMenuCommand.Lock) }

        assertIs<DesktopVaultState.Locked>(vault().state.value)
    }

    @Test
    fun execute_locked_ignoresNavigationCommands() {
        runBlocking { vault().lock() }
        val launchTab = mainPage.selectedTab

        listOf(DesktopMenuCommand.CoinManager, DesktopMenuCommand.About, DesktopMenuCommand.ShowSettings)
            .forEach(actions()::execute)

        assertEquals(listOf<HSPage>(mainPage), navigation.backStack.toList())
        assertEquals(launchTab, mainPage.selectedTab)
    }

    private fun vault(): DesktopVault = GlobalContext.get().get()

    private fun actions(scope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)) =
        DesktopMenuActions(
            navigation = navigation,
            vault = vault(),
            accountManager = mockk<IAccountManager>(relaxed = true) { every { activeAccount } returns account },
            appearanceStore = GlobalContext.get().get(),
            balanceHiddenManager = mockk(relaxed = true),
            appExit = mockk(relaxed = true),
            urlOpener = mockk(relaxed = true),
            scope = scope,
        )
}
