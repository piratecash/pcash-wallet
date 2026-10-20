package cash.p.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder

/** Recreates [content] from its saved state only, as the desktop lock/unlock does; `StateRestorationTester` is not implemented on desktop. */
@Composable
internal fun RecreatableContent(alive: Boolean, content: @Composable () -> Unit) {
    val holder = rememberSaveableStateHolder()
    if (alive) {
        holder.SaveableStateProvider(CONTENT_KEY) { content() }
    }
}

private const val CONTENT_KEY = "content"
