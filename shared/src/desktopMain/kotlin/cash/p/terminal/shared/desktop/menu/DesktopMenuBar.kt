package cash.p.terminal.shared.desktop.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.MenuBarScope
import androidx.compose.ui.window.MenuScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.core.AppearanceSettingsStore
import cash.p.terminal.core.providers.SharedAppConfig
import cash.p.terminal.modules.backuplocal.fullbackup.BackupManagerPage
import cash.p.terminal.modules.balance.supportsCoinManager
import cash.p.terminal.modules.manageaccount.ManageAccountPage
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.manageaccounts.ManageAccountsPage
import cash.p.terminal.modules.managewallets.ManageWalletsPage
import cash.p.terminal.modules.releasenotes.ReleaseNotesPage
import cash.p.terminal.modules.settings.about.AboutPage
import cash.p.terminal.modules.settings.about.ContactOptionsSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.BackupManager_Title
import cash.p.terminal.resources.Balance_Title
import cash.p.terminal.resources.Button_Refresh
import cash.p.terminal.resources.ManageAccount_SwitchWallet_Title
import cash.p.terminal.resources.ManageCoins_title
import cash.p.terminal.resources.Market_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SettingsAboutApp_Site
import cash.p.terminal.resources.SettingsAboutApp_Title
import cash.p.terminal.resources.SettingsContact_Title
import cash.p.terminal.resources.SettingsSecurity_ManageKeys
import cash.p.terminal.resources.Settings_Title
import cash.p.terminal.resources.Transactions_Title
import cash.p.terminal.resources.desktop_menu_file
import cash.p.terminal.resources.desktop_menu_help
import cash.p.terminal.resources.desktop_menu_minimize
import cash.p.terminal.resources.desktop_menu_quit
import cash.p.terminal.resources.desktop_menu_view
import cash.p.terminal.resources.desktop_menu_wallet
import cash.p.terminal.resources.desktop_menu_wallet_settings
import cash.p.terminal.resources.desktop_menu_whats_new
import cash.p.terminal.resources.desktop_menu_window
import cash.p.terminal.resources.desktop_security_lock_now
import cash.p.terminal.resources.hide_balance
import cash.p.terminal.shared.desktop.DesktopAppExit
import cash.p.terminal.shared.desktop.lock.DesktopRoot
import cash.p.terminal.shared.desktop.qrscanner.DesktopOs
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DesktopVaultState
import cash.p.terminal.shared.main.DesktopMainPage
import cash.p.terminal.shared.main.shownTab
import cash.p.terminal.ui_compose.ModalOverlayTracker
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.awt.Desktop

private val isMac = DesktopOs.current == DesktopOs.MacOs

/** The app root with its menu bar, so the launcher module never sees the navigation types. */
@Composable
fun FrameWindowScope.DesktopRootWithMenu(wipeFailed: Boolean) {
    DesktopRoot(wipeFailed = wipeFailed, menu = { vaultState, navigation -> DesktopMenuBar(vaultState, navigation) })
}

@Composable
private fun FrameWindowScope.DesktopMenuBar(vaultState: DesktopVaultState, navigation: HSNavigation) {
    val accountManager = koinInject<IAccountManager>()
    val appearanceStore = koinInject<AppearanceSettingsStore>()
    val balanceHiddenManager = koinInject<IBalanceHiddenManager>()
    val vault = koinInject<DesktopVault>()
    val appExit = koinInject<DesktopAppExit>()
    val urlOpener = koinInject<UrlOpener>()
    val scope = rememberCoroutineScope()
    val actions = remember(navigation) {
        DesktopMenuActions(
            navigation, vault, accountManager, appearanceStore, balanceHiddenManager, appExit, urlOpener, scope,
        )
    }
    val activeAccount by remember(accountManager) {
        accountManager.activeAccountStateFlow.map { (it as? ActiveAccountState.ActiveAccount)?.account }
    }.collectAsStateWithLifecycle(accountManager.activeAccount)
    val marketsTabEnabled by appearanceStore.marketsTabEnabledFlow.collectAsStateWithLifecycle()
    val balanceHidden by balanceHiddenManager.balanceHiddenFlow.collectAsStateWithLifecycle()
    val context = desktopMenuContext(
        unlocked = vaultState is DesktopVaultState.Unlocked,
        navigation = navigation,
        activeAccount = activeAccount,
        marketsTabEnabled = marketsTabEnabled,
    )
    val onCommand: (DesktopMenuCommand) -> Unit = { command ->
        if (command == DesktopMenuCommand.Minimize) window.isMinimized = true else actions.execute(command)
    }
    if (isMac) MacAppMenuHandlers(actions)
    MenuBar {
        if (!isMac) FileMenu(context, onCommand)
        WalletMenu(context, onCommand)
        ViewMenu(context, balanceHidden, onCommand)
        if (isMac) {
            Menu(stringResource(Res.string.desktop_menu_window)) {
                CommandItem(DesktopMenuCommand.Minimize, Res.string.desktop_menu_minimize, context, onCommand, Key.M)
            }
        }
        HelpMenu(context, onCommand)
    }
}

/** Runs a menu command, re-checking it against the state at the moment it fires: a menu may lag a frame. */
class DesktopMenuActions(
    private val navigation: HSNavigation,
    private val vault: DesktopVault,
    private val accountManager: IAccountManager,
    private val appearanceStore: AppearanceSettingsStore,
    private val balanceHiddenManager: IBalanceHiddenManager,
    private val appExit: DesktopAppExit,
    private val urlOpener: UrlOpener,
    private val scope: CoroutineScope,
) {
    fun execute(command: DesktopMenuCommand) {
        val context = desktopMenuContext(
            unlocked = vault.state.value is DesktopVaultState.Unlocked,
            navigation = navigation,
            activeAccount = accountManager.activeAccount,
            marketsTabEnabled = appearanceStore.marketsTabEnabledFlow.value,
        )
        if (!command.isEnabled(context)) return
        val tab = command.tab
        if (tab != null) navigation.mainPage()?.selectTab(tab) else perform(command)
    }

    private fun perform(command: DesktopMenuCommand) {
        when (command) {
            DesktopMenuCommand.Lock -> scope.launch { vault.lock() }
            DesktopMenuCommand.ToggleBalanceHidden -> balanceHiddenManager.toggleBalanceHidden()
            DesktopMenuCommand.Refresh -> navigation.mainPage()?.refreshBalance()
            DesktopMenuCommand.Website -> urlOpener.open(SharedAppConfig.companyWebPageLink)
            DesktopMenuCommand.Quit -> appExit.request()
            else -> open(command)
        }
    }

    private fun open(command: DesktopMenuCommand) {
        when (command) {
            DesktopMenuCommand.About -> navigation.slideFromRight(AboutPage())
            DesktopMenuCommand.CoinManager -> navigation.slideFromRight(ManageWalletsPage())
            DesktopMenuCommand.SwitchWallet ->
                navigation.slideFromBottom(ManageAccountsPage(ManageAccountsModule.Mode.Switcher))
            DesktopMenuCommand.ManageWallets ->
                navigation.slideFromRight(ManageAccountsPage(ManageAccountsModule.Mode.Manage))
            DesktopMenuCommand.WalletSettings -> accountManager.activeAccount?.let {
                navigation.slideFromRight(ManageAccountPage(ManageAccountPage.Input(it.id)))
            }
            DesktopMenuCommand.Backups -> navigation.slideFromRight(BackupManagerPage())
            DesktopMenuCommand.WhatsNew -> navigation.slideFromRight(ReleaseNotesPage(ReleaseNotesPage.Input(false)))
            DesktopMenuCommand.Contact -> navigation.slideFromBottom(ContactOptionsSheet(null))
            else -> Unit
        }
    }
}

internal fun desktopMenuContext(
    unlocked: Boolean,
    navigation: HSNavigation,
    activeAccount: Account?,
    marketsTabEnabled: Boolean,
) = DesktopMenuContext(
    unlocked = unlocked,
    selectedTab = navigation.mainPage()?.let { shownTab(it.selectedTab, marketsTabEnabled) },
    marketsTabEnabled = marketsTabEnabled,
    hasActiveAccount = activeAccount != null,
    coinManagerSupported = activeAccount?.type?.supportsCoinManager == true,
)

/** The main page, only while nothing covers it: no page, no sheet on the back stack or in its own composition. */
private fun HSNavigation.mainPage(): DesktopMainPage? =
    (backStack.singleOrNull() as? DesktopMainPage)?.takeUnless { ModalOverlayTracker.hasForegroundModal }

/** The system About and Settings items cannot be greyed out; unavailable, they do nothing. */
@Composable
private fun MacAppMenuHandlers(actions: DesktopMenuActions) {
    DisposableEffect(actions) {
        val desktop = Desktop.getDesktop()
        desktop.setAboutHandler { actions.execute(DesktopMenuCommand.About) }
        desktop.setPreferencesHandler { actions.execute(DesktopMenuCommand.ShowSettings) }
        onDispose {
            desktop.setAboutHandler(null)
            desktop.setPreferencesHandler(null)
        }
    }
}

@Composable
private fun MenuBarScope.FileMenu(context: DesktopMenuContext, onCommand: (DesktopMenuCommand) -> Unit) {
    Menu(stringResource(Res.string.desktop_menu_file)) {
        CommandItem(DesktopMenuCommand.ShowSettings, Res.string.Settings_Title, context, onCommand, Key.Comma)
        Separator()
        CommandItem(DesktopMenuCommand.Quit, Res.string.desktop_menu_quit, context, onCommand, Key.Q)
    }
}

@Composable
private fun MenuBarScope.WalletMenu(context: DesktopMenuContext, onCommand: (DesktopMenuCommand) -> Unit) {
    Menu(stringResource(Res.string.desktop_menu_wallet)) {
        CommandItem(DesktopMenuCommand.Lock, Res.string.desktop_security_lock_now, context, onCommand, Key.L)
        Separator()
        CommandItem(DesktopMenuCommand.CoinManager, Res.string.ManageCoins_title, context, onCommand, Key.K)
        CommandItem(DesktopMenuCommand.SwitchWallet, Res.string.ManageAccount_SwitchWallet_Title, context, onCommand)
        CommandItem(DesktopMenuCommand.ManageWallets, Res.string.SettingsSecurity_ManageKeys, context, onCommand)
        CommandItem(DesktopMenuCommand.WalletSettings, Res.string.desktop_menu_wallet_settings, context, onCommand)
        CommandItem(DesktopMenuCommand.Backups, Res.string.BackupManager_Title, context, onCommand)
    }
}

@Composable
private fun MenuBarScope.ViewMenu(
    context: DesktopMenuContext,
    balanceHidden: Boolean,
    onCommand: (DesktopMenuCommand) -> Unit,
) {
    Menu(stringResource(Res.string.desktop_menu_view)) {
        CommandItem(DesktopMenuCommand.ShowBalance, Res.string.Balance_Title, context, onCommand, Key.One)
        CommandItem(DesktopMenuCommand.ShowTransactions, Res.string.Transactions_Title, context, onCommand, Key.Two)
        CommandItem(DesktopMenuCommand.ShowMarket, Res.string.Market_Title, context, onCommand, Key.Three)
        CommandItem(DesktopMenuCommand.ShowSettings, Res.string.Settings_Title, context, onCommand, Key.Four)
        Separator()
        CheckboxItem(
            text = stringResource(Res.string.hide_balance),
            checked = balanceHidden,
            enabled = DesktopMenuCommand.ToggleBalanceHidden.isEnabled(context),
            shortcut = shortcut(Key.H, shift = true),
            onCheckedChange = { onCommand(DesktopMenuCommand.ToggleBalanceHidden) },
        )
        CommandItem(DesktopMenuCommand.Refresh, Res.string.Button_Refresh, context, onCommand, Key.R)
    }
}

@Composable
private fun MenuBarScope.HelpMenu(context: DesktopMenuContext, onCommand: (DesktopMenuCommand) -> Unit) {
    Menu(stringResource(Res.string.desktop_menu_help)) {
        CommandItem(DesktopMenuCommand.WhatsNew, Res.string.desktop_menu_whats_new, context, onCommand)
        CommandItem(DesktopMenuCommand.Contact, Res.string.SettingsContact_Title, context, onCommand)
        CommandItem(DesktopMenuCommand.Website, Res.string.SettingsAboutApp_Site, context, onCommand)
        if (!isMac) CommandItem(DesktopMenuCommand.About, Res.string.SettingsAboutApp_Title, context, onCommand)
    }
}

@Composable
private fun MenuScope.CommandItem(
    command: DesktopMenuCommand,
    title: StringResource,
    context: DesktopMenuContext,
    onCommand: (DesktopMenuCommand) -> Unit,
    key: Key? = null,
) {
    Item(
        text = stringResource(title),
        enabled = command.isEnabled(context),
        shortcut = key?.let(::shortcut),
        onClick = { onCommand(command) },
    )
}

private fun shortcut(key: Key, shift: Boolean = false) =
    KeyShortcut(key = key, ctrl = !isMac, meta = isMac, shift = shift)
