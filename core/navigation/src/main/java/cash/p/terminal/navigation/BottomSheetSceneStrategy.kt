package cash.p.terminal.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/** An [OverlayScene] that renders an [entry] within a [ModalBottomSheet]. */
@OptIn(ExperimentalMaterial3Api::class)
internal class BottomSheetScene<T : Any>(
    override val key: Any,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val entry: NavEntry<T>,
    private val modalBottomSheetProperties: ModalBottomSheetProperties,
    private val colors: BottomSheetColors,
    private val isLocked: State<Boolean>,
    private val onBack: () -> Unit,
) : OverlayScene<T> {

    override val entries: List<NavEntry<T>> = listOf(entry)

    // The state of the sheet currently on screen, so onRemove can animate it out. Null while the
    // sheet is not composed (see the lock gate below).
    private var sheetState: SheetState? = null

    override val content: @Composable (() -> Unit) = {
        // The sheet's own window would sit above the lock overlay: keep the entry on the stack but
        // do not compose the sheet while locked.
        if (!isLocked.value) {
            val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            DisposableEffect(state) {
                sheetState = state
                onDispose {
                    if (sheetState === state) sheetState = null
                }
            }
            val dismissRegistry = remember { BottomSheetDismissRegistry() }
            ModalBottomSheet(
                onDismissRequest = {
                    val handler = dismissRegistry.handler
                    if (handler != null) handler() else onBack()
                },
                sheetState = state,
                containerColor = colors.container,
                scrimColor = colors.scrim,
                dragHandle = null,
                // Only the top inset: the sheet content pads the bottom one itself, so its
                // background reaches under the navigation bar.
                contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Top) },
                properties = modalBottomSheetProperties,
            ) {
                CompositionLocalProvider(LocalBottomSheetDismissRegistry provides dismissRegistry) {
                    entry.Content()
                }
            }
        }
    }

    // NavDisplay keeps a popped overlay scene composed until this returns, so the sheet and its
    // scrim animate out on every pop, not only on the swipe/scrim-tap paths the sheet drives
    // itself. A pop that follows one of those finds the sheet already hidden and returns at once.
    override suspend fun onRemove() {
        val state = sheetState ?: return
        try {
            state.hide()
        } catch (e: CancellationException) {
            // A drag or another sheet animation interrupted the hide; the scene still has to go.
            if (!currentCoroutineContext().isActive) throw e
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BottomSheetScene<*>) return false

        return key == other.key &&
            previousEntries == other.previousEntries &&
            overlaidEntries == other.overlaidEntries &&
            entry == other.entry &&
            modalBottomSheetProperties == other.modalBottomSheetProperties &&
            colors == other.colors
    }

    override fun hashCode(): Int {
        return key.hashCode() * 31 +
            previousEntries.hashCode() * 31 +
            overlaidEntries.hashCode() * 31 +
            entry.hashCode() * 31 +
            modalBottomSheetProperties.hashCode() * 31 +
            colors.hashCode()
    }

    override fun toString(): String {
        return "BottomSheetScene(key=$key, entry=$entry, previousEntries=$previousEntries, " +
            "overlaidEntries=$overlaidEntries)"
    }
}

/** Theme colours of the sheet, supplied by the host (this module cannot see the app theme). */
data class BottomSheetColors(val container: Color, val scrim: Color)

/**
 * Displays entries whose [NavEntry.metadata] contains [bottomSheet] within a [ModalBottomSheet].
 * Must be listed before any non-overlay scene strategy.
 */
@OptIn(ExperimentalMaterial3Api::class)
class BottomSheetSceneStrategy<T : Any>(
    private val colors: BottomSheetColors,
    private val isLocked: State<Boolean>,
) : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val lastEntry = entries.lastOrNull() ?: return null
        val properties = lastEntry.metadata[BOTTOM_SHEET_KEY] as? ModalBottomSheetProperties ?: return null
        return BottomSheetScene(
            key = lastEntry.contentKey,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            entry = lastEntry,
            modalBottomSheetProperties = properties,
            colors = colors,
            isLocked = isLocked,
            onBack = onBack,
        )
    }

    companion object {
        /** Marks an entry's metadata so the entry is displayed within a [ModalBottomSheet]. */
        fun bottomSheet(): Map<String, Any> = mapOf(BOTTOM_SHEET_KEY to ModalBottomSheetProperties())

        private const val BOTTOM_SHEET_KEY = "bottomsheet"
    }
}
