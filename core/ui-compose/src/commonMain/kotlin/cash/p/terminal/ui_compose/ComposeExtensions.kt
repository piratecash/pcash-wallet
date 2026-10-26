package cash.p.terminal.ui_compose

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.first
import kotlin.time.TimeSource

/**
 * Suspends until the soft keyboard (IME) is fully hidden.
 *
 * Material3 `ModalBottomSheet` hard-codes an internal `imePadding`, so opening it while the
 * keyboard is still retracting makes the sheet start raised and slide down mid-animation,
 * leaving a visible gap. Awaiting this before showing such a sheet lets it appear in its
 * final position. Read [WindowInsets.Companion.ime] in a composable scope and pass it here.
 */
suspend fun WindowInsets.awaitImeHidden(density: Density) {
    snapshotFlow { getBottom(density) }.first { it == 0 }
}

@Composable
fun Modifier.oneLineHeight(textStyle: TextStyle) =
    this
        .height(with(LocalDensity.current) {
            textStyle.lineHeight.toDp()
        })
        .wrapContentHeight(Alignment.CenterVertically)

@Composable
fun <T> rememberDebouncedAction(
    timeoutMs: Long = 500,
    action: (T) -> Unit
): (T) -> Unit {
    val actionState by rememberUpdatedState(action)
    val lastClick = remember { mutableStateOf<TimeSource.Monotonic.ValueTimeMark?>(null) }

    return remember(timeoutMs) {
        { param: T ->
            val last = lastClick.value
            if (last == null || last.elapsedNow().inWholeMilliseconds > timeoutMs) {
                lastClick.value = TimeSource.Monotonic.markNow()
                actionState(param)
            }
        }
    }
}
