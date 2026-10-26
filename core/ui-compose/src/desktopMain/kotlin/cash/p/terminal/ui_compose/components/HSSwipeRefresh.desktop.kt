package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFold
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Compose drops wheel/touchpad scrolling the content cannot take and never flings it, so the pull
 * would never start at the top and, once started by scroll leftovers, would never be released.
 * Feed unconsumed scrolling to the pull and release it once scrolling goes quiet, like a lifted finger.
 */
@Composable
internal actual fun PullOnPointerScroll(content: @Composable () -> Unit) {
    val dispatcher = remember { NestedScrollDispatcher() }
    val gesture = remember { WheelGesture() }
    val connection = remember {
        object : NestedScrollConnection {
            // Momentum that carried the content to the top must not turn into a pull.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) =
                if (gesture.scrolledContent) available else Offset.Zero
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .nestedScroll(connection, dispatcher)
            .pointerInput(dispatcher) {
                val pullPerScrollUnit = PULL_PER_SCROLL_UNIT.toPx()
                coroutineScope {
                    var release: Job? = null
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            if (event.changes.fastAny { it.isConsumed }) {
                                gesture.scrolledContent = true
                            } else if (!gesture.scrolledContent) {
                                val pull = Offset(0f, -event.verticalScrollDelta * pullPerScrollUnit)
                                dispatcher.dispatchPostScroll(Offset.Zero, pull, NestedScrollSource.UserInput)
                            }
                            release?.cancel()
                            release = launch {
                                delay(SCROLL_END_DELAY_MS)
                                gesture.scrolledContent = false
                                dispatcher.dispatchPreFling(Velocity.Zero)
                            }
                        }
                    }
                }
            },
    ) {
        content()
    }
}

private class WheelGesture {
    var scrolledContent = false
}

private val PointerEvent.verticalScrollDelta
    get() = changes.fastFold(0f) { sum, change -> sum + change.scrollDelta.y }

// Five wheel notches reach the refresh threshold, as does a deliberate touchpad swipe.
private val PULL_PER_SCROLL_UNIT = 32.dp

// Long enough to outlast the smooth-scroll animation of a wheel notch and the gaps in touchpad momentum.
private const val SCROLL_END_DELAY_MS = 300L
