package cash.p.terminal.modules.main

import android.os.Looper
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.annotation.StringRes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.AndroidUiFrameClock
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import cash.p.terminal.R
import cash.p.terminal.navigation.HSNavigation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowChoreographer
import java.time.Duration

/**
 * Hosts [Nav3Host] on a production-like recomposer: the compose test rule's interceptor runs a `launch`
 * body even when its scope is already cancelled, which hides bugs of callbacks bound to a disposed page.
 */
internal class AndroidRecomposerNavHost(
    navigation: HSNavigation,
    activityResultRegistryOwner: ActivityResultRegistryOwner? = null,
) {

    private val controller = Robolectric.buildActivity(ComponentActivity::class.java)
    private val activity: ComponentActivity = controller.get()
    private val composeView: ComposeView
    private val recomposerScope: CoroutineScope

    init {
        // Frames only on clock advance: unpaused, infinite animations would keep the looper busy forever.
        ShadowChoreographer.setPaused(true)
        ShadowChoreographer.setFrameDelay(FRAME)
        // Compose's global AndroidUiDispatcher stalls after the first test's looper reset: use a fresh recomposer.
        recomposerScope = CoroutineScope(Dispatchers.Main + AndroidUiFrameClock(Choreographer.getInstance()) + Job())
        val recomposer = Recomposer(recomposerScope.coroutineContext)
        recomposerScope.launch { recomposer.runRecomposeAndApplyChanges() }
        // HUD snackbars require an AppCompat theme.
        activity.setTheme(R.style.Theme_AppTheme_DayNight)
        controller.setup()
        composeView = ComposeView(activity).apply {
            setParentCompositionContext(recomposer)
            setContent {
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides (activityResultRegistryOwner ?: activity),
                ) {
                    TestNav3Host(navigation)
                }
            }
        }
        activity.setContentView(composeView)
        advanceFrames()
    }

    fun clickOnText(@StringRes textRes: Int) {
        val text = activity.getString(textRes)
        val node = rootSemanticsNode().descendants().single { node ->
            node.config.getOrNull(SemanticsActions.OnClick) != null &&
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
        }
        checkNotNull(node.config[SemanticsActions.OnClick].action).invoke()
        advanceFrames()
    }

    fun advanceFrames() {
        repeat(FRAMES) {
            // GlobalSnapshotManager runs on that stalled dispatcher too, so apply state writes here.
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(FRAME)
        }
    }

    fun close() {
        controller.pause().stop().destroy()
        recomposerScope.cancel()
    }

    private fun rootSemanticsNode(): SemanticsNode =
        (composeView.getChildAt(0) as RootForTest).semanticsOwner.rootSemanticsNode

    private fun SemanticsNode.descendants(): List<SemanticsNode> =
        children.flatMap { listOf(it) + it.descendants() }

    private companion object {
        val FRAME: Duration = Duration.ofMillis(16)
        const val FRAMES = 125
    }
}
