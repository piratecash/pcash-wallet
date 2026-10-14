package cash.p.terminal.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/** Lifecycle of the screen hosting the navigation (the activity), provided by the root. */
val LocalHostLifecycleOwner: ProvidableCompositionLocal<LifecycleOwner> =
    staticCompositionLocalOf { error("LocalHostLifecycleOwner is not provided") }

/**
 * Fragment-style onResume/onPause for a page: active while the host is resumed and the page's entry
 * is at least STARTED. Unlike LifecycleResumeEffect it stays active under a bottom sheet, where the
 * entry is capped at STARTED. Leaving composition counts as pause.
 */
@Composable
fun PageResumeEffect(onResume: () -> Unit, onPause: () -> Unit) {
    val hostState by LocalHostLifecycleOwner.current.lifecycle.currentStateAsState()
    val entryState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val active = hostState.isAtLeast(Lifecycle.State.RESUMED) && entryState.isAtLeast(Lifecycle.State.STARTED)
    val currentOnResume by rememberUpdatedState(onResume)
    val currentOnPause by rememberUpdatedState(onPause)
    DisposableEffect(active) {
        if (active) currentOnResume()
        onDispose {
            if (active) currentOnPause()
        }
    }
}

/** Must wrap every page's content: the guarded navigation acts only for the page whose entry is RESUMED. */
@Composable
fun ReportPageResumed(navigation: HSNavigation, page: HSPage) {
    LifecycleResumeEffect(navigation, page) {
        navigation.onPageResumed(page)
        onPauseOrDispose { navigation.onPagePaused(page) }
    }
}
