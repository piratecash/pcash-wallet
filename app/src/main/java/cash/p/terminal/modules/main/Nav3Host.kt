package cash.p.terminal.modules.main

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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import cash.p.terminal.navigation.BottomSheetColors
import cash.p.terminal.navigation.BottomSheetSceneStrategy
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.LocalHostLifecycleOwner
import cash.p.terminal.navigation.ReportPageResumed
import cash.p.terminal.navigation.rememberSharedViewModelStoreNavEntryDecorator
import cash.p.terminal.ui_compose.LocalConnectionPanelState
import cash.p.terminal.ui_compose.ModalOverlayTracker
import cash.p.terminal.ui_compose.ScreenSecurityState
import cash.p.terminal.ui_compose.components.ConnectionStatusView
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.hideKeyboard

/** Root of the app's navigation; [isLocked] keeps sheets from drawing above the lock overlay. */
@Composable
fun Nav3Host(navigation: HSNavigation, isLocked: State<Boolean>) {
    val activity = checkNotNull(LocalActivity.current)
    ComposeAppTheme {
        CompositionLocalProvider(LocalHostLifecycleOwner provides LocalLifecycleOwner.current) {
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
                scrim = ComposeAppTheme.colors.modalOverlay,
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PageChrome(page: HSPage, navigation: HSNavigation, screenshotFlag: ScreenshotFlagController) {
    ReportPageResumed(navigation, page)
    if (page.bottomSheet) {
        // Runs in the sheet's own window, like BaseComposableBottomSheetFragment did.
        ModalOverlayTracker.TrackForeground()
        page.GetContent(navigation)
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
        page.GetContent(navigation)
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
