package cash.p.terminal.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// Never declare a page as `object`: resultKey, navType and uuid are per stack occurrence.
abstract class HSPage(
    val bottomSheet: Boolean = false,
    val screenshotEnabled: Boolean = true,
    val showConnectionPanel: Boolean = true,
) : NavKey {
    var resultKey: String? = null
    var navType: NavigationType = NavigationType.SlideFromRight
    val uuid: String = randomId()

    // Keys the entry's saveable state and ViewModel store, so two instances never share them.
    open fun contentKey(): String = "${this::class.simpleName}:$uuid"

    fun metadata(): Map<String, Any> = buildMap {
        if (bottomSheet) putAll(BottomSheetSceneStrategy.bottomSheet())
        putAll(transitionMetadata())
    }

    private fun transitionMetadata(): Map<String, Any> = when (navType) {
        NavigationType.SlideFromBottom -> NavDisplay.transitionSpec {
            slideInVertically(tween(300)) { it } togetherWith fadeOut(tween(400))
        } + NavDisplay.popTransitionSpec {
            fadeIn(tween(500)) togetherWith slideOutVertically(tween(300)) { it }
        }

        NavigationType.SlideFromRight -> NavDisplay.transitionSpec {
            slideInHorizontally(tween(300)) { it } togetherWith fadeOut(tween(400))
        } + NavDisplay.popTransitionSpec {
            fadeIn(tween(500)) togetherWith slideOutHorizontally(tween(300)) { it }
        }
    }

    @Composable
    abstract fun GetContent(navigation: HSNavigation)

    /** How every host renders a page: reports its resume state and binds result launchers to it. */
    @Composable
    fun Content(navigation: HSNavigation) {
        ReportPageResumed(navigation, this)
        CompositionLocalProvider(LocalHSPage provides this) { GetContent(navigation) }
    }
}

/** The page being composed; null outside any page (root-level content). */
internal val LocalHSPage = staticCompositionLocalOf<HSPage?> { null }

abstract class HSBottomSheet : HSPage(bottomSheet = true)

@OptIn(ExperimentalUuidApi::class)
internal fun randomId(): String = Uuid.random().toString()
