package cash.p.terminal.modules.main

import android.app.Activity
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.google.android.material.snackbar.BaseTransientBottomBar
import cash.p.terminal.R
import cash.p.terminal.ledger.domain.LedgerUserOperations
import cash.p.terminal.ledger.domain.LedgerWaitReason
import cash.p.terminal.navigation.BottomSheetColors
import cash.p.terminal.navigation.BottomSheetSceneStrategy
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.LocalHostLifecycleOwner
import cash.p.terminal.navigation.rememberSharedViewModelStoreNavEntryDecorator
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui_compose.LocalConnectionPanelState
import cash.p.terminal.ui_compose.ModalOverlayTracker
import cash.p.terminal.ui_compose.ScreenSecurityState
import cash.p.terminal.ui.helpers.LinkHelper
import cash.p.terminal.ui_compose.components.ConnectionStatusView
import cash.p.terminal.ui_compose.components.CustomSnackbar
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.LocalHudMessenger
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.hideKeyboard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Root of the app's navigation; [isLocked] keeps sheets from drawing above the lock overlay. */
@Composable
fun Nav3Host(
    navigation: HSNavigation,
    isLocked: State<Boolean>,
    ledgerUserOperations: LedgerUserOperations,
    ledgerOpeningApp: StateFlow<String?>,
) {
    val activity = checkNotNull(LocalActivity.current)
    val view = LocalView.current
    val hudMessenger = rememberHudMessenger()
    val urlOpener = remember(activity) { ActivityUrlOpener(activity) }
    ComposeAppTheme {
        CompositionLocalProvider(
            LocalHostLifecycleOwner provides LocalLifecycleOwner.current,
            LocalHudMessenger provides hudMessenger,
            LocalUrlOpener provides urlOpener,
        ) {
            LaunchedEffect(ledgerUserOperations, ledgerOpeningApp, hudMessenger, view) {
                fun show(message: Int, appName: String) =
                    hudMessenger.showWarning(Translator.getString(message, appName))
                launch {
                    ledgerUserOperations.yieldedNotices.collect { show(R.string.ledger_sync_paused_open_app, it) }
                }
                // Collected directly (no merge/buffer) so the delivery-time filter stays valid.
                launch { ledgerUserOperations.waitNotices.collect { show(it.reason.message, it.appName) } }
                launch { showLedgerOpeningHints(view, ledgerOpeningApp) }
                launch { showViewKeyExportHint(view, ledgerUserOperations.awaitingViewKeyExport) }
            }
            // Composed before the pages, so any page's own back handler takes precedence.
            BackHandler(enabled = navigation.backStack.size == 1) {
                activity.moveTaskToBack(true)
            }
            val topPage = navigation.lastOrNull()
            LaunchedEffect(topPage) {
                activity.currentFocus?.hideKeyboard(activity)
            }
            val screenshotFlag = rememberScreenshotFlagController(activity.window, isLocked)
            val colors = BottomSheetColors(
                container = ComposeAppTheme.colors.transparent,
                scrim = ComposeAppTheme.colors.backgroundOverlay,
            )
            val sheetStrategy = remember(colors, isLocked) {
                BottomSheetSceneStrategy<HSPage>(colors, isLocked)
            }
            NavDisplay(
                backStack = navigation.backStack,
                onBack = { navigation.navigateUp() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberSharedViewModelStoreNavEntryDecorator(),
                ),
                sceneStrategies = listOf(sheetStrategy),
                entryProvider = { page ->
                    NavEntry(page, page.contentKey(), page.metadata()) {
                        PageChrome(page, navigation, screenshotFlag)
                    }
                },
            )
        }
    }
}

private const val HINT_RESHOW_GRACE_MS = 3_000L

private suspend fun showLedgerOpeningHints(view: View, openingApp: StateFlow<String?>) =
    openingApp.collectLatest { appName ->
        if (appName == null) return@collectLatest
        showHintWhilePending(view, Translator.getString(R.string.ledger_confirm_opening_app, appName))
    }

private suspend fun showViewKeyExportHint(view: View, awaiting: Flow<Boolean>) =
    awaiting.collectLatest { pending ->
        if (pending) showHintWhilePending(view, Translator.getString(R.string.ledger_monero_confirm_view_key))
    }

/** Another snackbar replaces the hint while the Ledger still waits, so it comes back once that one is readable. */
private suspend fun showHintWhilePending(view: View, text: String) {
    while (true) {
        val snackbar = HudHelper.showWarningMessage(view, text, SnackbarDuration.INDEFINITE) ?: return
        try {
            if (snackbar.awaitDismissEvent() != BaseTransientBottomBar.BaseCallback.DISMISS_EVENT_CONSECUTIVE) return
        } finally {
            snackbar.dismiss()
        }
        delay(HINT_RESHOW_GRACE_MS)
    }
}

private suspend fun CustomSnackbar.awaitDismissEvent(): Int = suspendCancellableCoroutine { continuation ->
    val callback = object : BaseTransientBottomBar.BaseCallback<CustomSnackbar?>() {
        override fun onDismissed(transientBottomBar: CustomSnackbar?, event: Int) {
            removeCallback(this)
            if (continuation.isActive) continuation.resume(event, null)
        }
    }
    addCallback(callback)
    continuation.invokeOnCancellation { removeCallback(callback) }
}

private val LedgerWaitReason.message: Int
    get() = when (this) {
        LedgerWaitReason.Sync -> R.string.ledger_sync_finishing_wait
        LedgerWaitReason.DeviceRequest -> R.string.ledger_device_request_wait
    }

/** Custom tab only opens from an Activity context; an application context falls back to the external browser. */
private class ActivityUrlOpener(private val activity: Activity) : UrlOpener {
    override fun open(url: String) = LinkHelper.openLinkInAppBrowser(activity, url)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PageChrome(page: HSPage, navigation: HSNavigation, screenshotFlag: ScreenshotFlagController) {
    if (page.bottomSheet) {
        // Runs in the sheet's own window, like BaseComposableBottomSheetFragment did.
        ModalOverlayTracker.TrackForeground()
        page.Content(navigation)
        return
    }
    if (!page.screenshotEnabled) {
        DisposableEffect(screenshotFlag) {
            val registration = Any()
            screenshotFlag.onSecureEntryComposed(registration)
            onDispose { screenshotFlag.onSecureEntryDisposed(registration) }
        }
    }
    Box(
        // Exposes testTag values as resource-ids for UiAutomator (baseline profiles, UI tests).
        modifier = Modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
    ) {
        page.Content(navigation)
        if (page.showConnectionPanel && LocalConnectionPanelState.current.value) {
            ConnectionStatusView(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }
    }
}

@Composable
private fun rememberScreenshotFlagController(window: Window, isLocked: State<Boolean>): ScreenshotFlagController {
    val controller = remember(window) { ScreenshotFlagController(window) }
    LifecycleResumeEffect(controller) {
        controller.onResume()
        onPauseOrDispose { controller.onPause() }
    }
    LaunchedEffect(controller, isLocked) {
        snapshotFlow { isLocked.value }.collect { controller.onLockStateChanged() }
    }
    return controller
}

internal enum class ScreenshotFlagAction { Add, Clear, Keep }

// While locked, the lock screen owns the flag (MainActivity.applyLockWindowFlags): the calculator
// disguise stays screenshotable, and the opaque overlay hides the page beneath.
internal fun screenshotFlagAction(anySecureEntryComposed: Boolean, appLocked: Boolean): ScreenshotFlagAction =
    when {
        appLocked -> ScreenshotFlagAction.Keep
        anySecureEntryComposed -> ScreenshotFlagAction.Add
        else -> ScreenshotFlagAction.Clear
    }

/**
 * Secures the window while any page with screenshots disabled is still composed, including one
 * that is animating out, and always while the host is paused (recents snapshot).
 */
internal class ScreenshotFlagController(private val window: Window) {

    private val secureEntries = mutableSetOf<Any>()
    private var resumed = false

    fun onResume() {
        resumed = true
        apply()
    }

    fun onPause() {
        resumed = false
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    // Unlocking hands the flag back from the lock screen to the pages.
    fun onLockStateChanged() = apply()

    fun onSecureEntryComposed(registration: Any) {
        secureEntries += registration
        apply()
    }

    fun onSecureEntryDisposed(registration: Any) {
        secureEntries -= registration
        apply()
    }

    private fun apply() {
        if (!resumed) return
        when (screenshotFlagAction(secureEntries.isNotEmpty(), ScreenSecurityState.isAppLocked)) {
            ScreenshotFlagAction.Add -> window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            ScreenshotFlagAction.Clear -> window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            ScreenshotFlagAction.Keep -> Unit
        }
    }
}
