package cash.p.terminal.shared.desktop.lock

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.shared.PcashApp
import cash.p.terminal.shared.desktop.vault.DesktopVault
import cash.p.terminal.shared.desktop.vault.DesktopVaultState
import cash.p.terminal.shared.main.DesktopMainPage
import cash.p.terminal.strings.helpers.LocaleType
import io.horizontalsystems.core.DesktopLanguageManager
import org.koin.compose.koinInject

/**
 * The window content: the password gate or, once unlocked, the app itself — never both, so no part
 * of the app can be reached while the lock is up. Its back stack, ViewModels and saved state live
 * above the two, so unlocking returns to the screen the user left.
 */
@Composable
fun DesktopRoot(appVersion: String, modifier: Modifier = Modifier) {
    val vault = koinInject<DesktopVault>()
    val languageManager = koinInject<DesktopLanguageManager>()
    val vaultState by vault.state.collectAsStateWithLifecycle()
    val localeTag by languageManager.localeTag.collectAsStateWithLifecycle()

    DesktopRootContent(
        vaultState = vaultState,
        localeTag = localeTag,
        rootPage = { DesktopMainPage(appVersion) },
        appContent = { navigation -> PcashApp(navigation) },
        modifier = modifier,
    )
}

@Composable
internal fun DesktopRootContent(
    vaultState: DesktopVaultState,
    localeTag: String,
    rootPage: () -> HSPage,
    appContent: @Composable (HSNavigation) -> Unit,
    modifier: Modifier = Modifier,
) {
    var vaultGeneration by remember { mutableIntStateOf(0) }
    LaunchedEffect(vaultState) {
        // A fresh storage is a fresh app: its back stack, its state and its ViewModels start over.
        if (vaultState == DesktopVaultState.NoVault) vaultGeneration++
    }

    // Keyed by the language: a composition reads the locale of the resources when it is created.
    key(localeTag) {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirectionFor(localeTag)) {
            Box(modifier = modifier.fillMaxSize()) {
                key(vaultGeneration) {
                    // Outlives the lock: the ViewModel stores of the pages hang off this owner.
                    SessionViewModelStore {
                        val navigation = remember { HSNavigation(NavBackStack(rootPage())) }
                        val appState = rememberSaveableStateHolder()
                        if (vaultState is DesktopVaultState.Unlocked) {
                            appState.SaveableStateProvider(APP_STATE_KEY) { appContent(navigation) }
                        }
                    }
                }
                when (vaultState) {
                    DesktopVaultState.NoVault -> LockScreen { CreatePasswordScreen() }
                    DesktopVaultState.Locked -> LockScreen { UnlockGate() }
                    is DesktopVaultState.Unlocked -> Unit
                }
            }
        }
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

@Composable
private fun UnlockGate() {
    var dataUnreadable by remember { mutableStateOf(false) }
    var forgotPassword by remember { mutableStateOf(false) }
    if (forgotPassword) {
        ForgotPasswordScreen(
            dataUnreadable = dataUnreadable,
            onCancelClick = {
                forgotPassword = false
                dataUnreadable = false
            },
        )
    } else {
        UnlockScreen(
            onForgotPasswordClick = { forgotPassword = true },
            onDataUnreadable = {
                dataUnreadable = true
                forgotPassword = true
            },
        )
    }
}

private fun layoutDirectionFor(localeTag: String): LayoutDirection =
    if (localeTag.startsWith(LocaleType.fa.tag)) LayoutDirection.Rtl else LayoutDirection.Ltr

private const val APP_STATE_KEY = "app"
