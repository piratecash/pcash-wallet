package cash.p.terminal.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/** Where a sheet's content registers its [BottomSheetDismissHandler]; owned by the sheet scene. */
internal class BottomSheetDismissRegistry {
    var handler: (() -> Unit)? = null
}

internal val LocalBottomSheetDismissRegistry =
    staticCompositionLocalOf<BottomSheetDismissRegistry?> { null }

/**
 * Replaces the default pop when the user dismisses the enclosing sheet by swipe, scrim tap or back;
 * [onDismiss] then owns popping the entry. The sheet's own buttons are not affected.
 */
@Composable
fun BottomSheetDismissHandler(onDismiss: () -> Unit) {
    val registry = LocalBottomSheetDismissRegistry.current ?: return
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(registry) {
        val handler = { currentOnDismiss() }
        registry.handler = handler
        onDispose {
            if (registry.handler === handler) registry.handler = null
        }
    }
}
