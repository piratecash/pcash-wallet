package cash.p.terminal.shared.desktop.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.Ref
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import cash.p.terminal.core.WalletOnboardingStorage
import cash.p.terminal.core.rememberTermsAcceptedAction
import cash.p.terminal.modules.intro.IntroScreen
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.shared.PcashApp
import cash.p.terminal.shared.desktop.DesktopAppExit
import cash.p.terminal.resources.Hud_Text_Created
import cash.p.terminal.resources.Res
import cash.p.terminal.shared.desktop.DesktopAppRestarter
import cash.p.terminal.shared.desktop.DesktopHiddenWalletEntry
import cash.p.terminal.shared.desktop.DesktopWipeCoordinator
import cash.p.terminal.shared.desktop.WipeProgress
import cash.p.terminal.shared.desktop.rememberDesktopDarkTheme
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DesktopStorageSession
import cash.p.terminal.shared.desktop.vault.DesktopVaultState
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import cash.p.terminal.shared.main.DesktopHudMessenger
import cash.p.terminal.shared.main.DesktopMainPage
import cash.p.terminal.strings.helpers.LocaleType
import cash.p.terminal.ui_compose.components.HSCircularProgressIndicator
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.DesktopLanguageManager
import kotlinx.coroutines.flow.Flow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The window content: the password gate or, once unlocked, the app itself — never both, so no part
 * of the app can be reached while the lock is up. Its back stack, ViewModels and saved state live
 * above the two, so unlocking returns to the screen the user left, within one storage session.
 */
@Composable
fun DesktopRoot(
    wipeFailed: Boolean,
    modifier: Modifier = Modifier,
    menu: @Composable (DesktopVaultState, HSNavigation) -> Unit = { _, _ -> },
) {
    val vault = koinInject<DesktopVault>()
    val languageManager = koinInject<DesktopLanguageManager>()
    val vaultState by vault.state.collectAsStateWithLifecycle()
    val localeTag by languageManager.localeTag.collectAsStateWithLifecycle()
    val appGeneration by koinInject<DesktopAppRestarter>().generation.collectAsStateWithLifecycle()
    val wipeProgress by koinInject<DesktopWipeCoordinator>().progress.collectAsStateWithLifecycle()
    val appExit = koinInject<DesktopAppExit>()
    val hiddenWalletEntry = koinInject<DesktopHiddenWalletEntry>()
    val enteringHiddenWallet by hiddenWalletEntry.running.collectAsStateWithLifecycle(initialValue = false)

    DesktopRootContent(
        vaultState = vaultState,
        localeTag = localeTag,
        appGeneration = appGeneration,
        levelChanges = koinInject<UserManager>().userLevelChangedFlow,
        rootPage = { DesktopMainPage() },
        appContent = { navigation ->
            OpenCreateWalletForNewHiddenWallet(navigation)
            PcashApp(navigation)
        },
        wipeFailed = wipeFailed,
        enteringHiddenWallet = enteringHiddenWallet,
        modifier = modifier,
        menu = menu,
    )
    (wipeProgress as? WipeProgress.RestartManually)?.let {
        RestartManuallyDialog(outcome = it.outcome, onConfirm = appExit::request)
    }
}

@Composable
internal fun DesktopRootContent(
    vaultState: DesktopVaultState,
    localeTag: String,
    appGeneration: Int,
    levelChanges: Flow<Int>,
    rootPage: () -> HSPage,
    appContent: @Composable (HSNavigation) -> Unit,
    wipeFailed: Boolean,
    modifier: Modifier = Modifier,
    enteringHiddenWallet: Boolean = false,
    menu: @Composable (DesktopVaultState, HSNavigation) -> Unit = { _, _ -> },
) {
    var vaultGeneration by remember { mutableIntStateOf(0) }
    LaunchedEffect(vaultState) {
        // A fresh storage is a fresh app: its back stack, its state and its ViewModels start over.
        if (vaultState == DesktopVaultState.NoVault) vaultGeneration++
    }
    // Only a new session starts over: a lock keeps the last one, so unlocking returns to the same screen.
    val shownSession = remember { Ref<DesktopStorageSession>() }
    (vaultState as? DesktopVaultState.Unlocked)?.let { shownSession.value = it.session }

    // Keyed by the language: a composition reads the locale of the resources when it is created.
    key(localeTag) {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirectionFor(localeTag)) {
            Box(modifier = modifier.fillMaxSize().background(ComposeAppTheme.colors.backgroundBase)) {
                // An app restart (after a full backup restore) starts over the same way; the storage stays open.
                key(vaultGeneration, appGeneration, shownSession.value) {
                    // Outlives the lock: the ViewModel stores of the pages hang off this owner.
                    SessionViewModelStore {
                        val root = remember { rootPage() }
                        val navigation = remember { HSNavigation(NavBackStack(root)) }
                        LaunchedEffect(navigation) {
                            // As on Android: a level change while the app is up returns to the main screen.
                            levelChanges.collect { navigation.removeLastUntil(root::class, inclusive = false) }
                        }
                        // In every vault state: one menu bar per session, never swapped on lock or unlock.
                        menu(vaultState, navigation)
                        val appState = rememberSaveableStateHolder()
                        if (vaultState is DesktopVaultState.Unlocked) {
                            appState.SaveableStateProvider(APP_STATE_KEY) { appContent(navigation) }
                        }
                    }
                }
                when (vaultState) {
                    DesktopVaultState.NoVault -> LockScreen { FirstRunGate() }
                    DesktopVaultState.Locked ->
                        if (enteringHiddenWallet) EnteringHiddenWallet() else LockScreen { UnlockGate(wipeFailed) }
                    is DesktopVaultState.Unlocked -> Unit
                }
            }
        }
    }
}

/** As Android after creating a hidden wallet: the new wallet is entered and asked for its first account. */
@Composable
private fun OpenCreateWalletForNewHiddenWallet(navigation: HSNavigation) {
    val entry = koinInject<DesktopHiddenWalletEntry>()
    val vault = koinInject<DesktopVault>()
    val hudMessenger = koinInject<DesktopHudMessenger>()
    val createdMessage = stringResource(Res.string.Hud_Text_Created)
    val createAccountPage = koinInject<AppPages>().createAccount()
    val openAfterTerms = navigation.rememberTermsAcceptedAction<HSPage>(navigation::slideFromRight)
    LaunchedEffect(navigation) {
        entry.awaitEntered()
        // The lock keeps the session open: only the state says whether this screen is still the one shown.
        val unlocked = vault.state.value as? DesktopVaultState.Unlocked ?: return@LaunchedEffect
        val rootSlot = unlocked.session.rootSlot
        if (entry.consume(rootSlot)) {
            hudMessenger.showSuccess(createdMessage)
            openAfterTerms(createAccountPage)
        }
    }
}

/** Instead of the unlock form: the user typed the password already, and the new wallet opens on its own. */
@Composable
private fun EnteringHiddenWallet() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        HSCircularProgressIndicator()
    }
}

/** The password screens: their ViewModels, and with them the typed password, end with the screen. */
@Composable
private fun LockScreen(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        // Outside the app's navigation host, and a text field's context menu is a focusable popup,
        // which requires a dispatcher owner of its own.
        LocalNavigationEventDispatcherOwner provides rememberNavigationEventDispatcherOwner(parent = null)
    ) {
        SessionViewModelStore(content)
    }
}

/** The intro runs before the first password, as on Android before the first main screen. */
@Composable
private fun FirstRunGate() {
    val onboardingStorage = koinInject<WalletOnboardingStorage>()
    // Read each time the gate appears: the wipe clears the flag with the other plain settings.
    var introPending by remember { mutableStateOf(!onboardingStorage.mainShowedOnce) }
    if (introPending) {
        IntroScreen(nightMode = rememberDesktopDarkTheme(), onFinish = { introPending = false })
    } else {
        CreatePasswordScreen()
    }
}

@Composable
private fun UnlockGate(wipeFailed: Boolean) {
    var unusable by remember { mutableStateOf<UnlockOutcome?>(null) }
    var forgotPassword by remember { mutableStateOf(false) }
    if (forgotPassword) {
        ForgotPasswordScreen(
            dataUnreadable = unusable == UnlockOutcome.Corrupted,
            legacyFormat = unusable == UnlockOutcome.LegacyFormat,
            onCancelClick = {
                forgotPassword = false
                unusable = null
            },
        )
    } else {
        UnlockScreen(
            wipeFailed = wipeFailed,
            onForgotPasswordClick = { forgotPassword = true },
            onStorageUnusable = {
                unusable = it
                forgotPassword = true
            },
        )
    }
}

private fun layoutDirectionFor(localeTag: String): LayoutDirection =
    if (localeTag.startsWith(LocaleType.fa.tag)) LayoutDirection.Rtl else LayoutDirection.Ltr

private const val APP_STATE_KEY = "app"
