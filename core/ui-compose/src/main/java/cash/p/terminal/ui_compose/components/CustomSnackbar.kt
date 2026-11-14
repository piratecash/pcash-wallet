package cash.p.terminal.ui_compose.components

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.coordinatorlayout.widget.CoordinatorLayout
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import com.google.android.material.snackbar.BaseTransientBottomBar
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource


// Snackbar placement on screen
enum class SnackbarGravity(val value: Int) {
    TOP(1),
    BOTTOM(2),
    TOP_OF_VIEW(3),
    BOTTOM_OF_VIEW(4)
}

class CustomSnackbar(
    parent: ViewGroup,
    content: View,
    contentViewCallback: ContentViewCallback
) : BaseTransientBottomBar<CustomSnackbar?>(parent, content, contentViewCallback) {

    init {
        getView().setBackgroundColor(Color.TRANSPARENT)
        getView().setPadding(0, 0, 0, 0)
    }

    class ContentViewCallback(private val view: View) :
        com.google.android.material.snackbar.ContentViewCallback {
        override fun animateContentIn(delay: Int, duration: Int) {}

        override fun animateContentOut(delay: Int, duration: Int) {}
    }

    companion object {
        // TODO: deliver snackbars through a Compose snackbar host once the redesign and Nav3 are in master
        fun make(
            contentView: View,
            text: String,
            variant: AppSnackbarVariant,
            duration: SnackbarDuration,
            gravity: SnackbarGravity,
            @DrawableRes iconRes: Int?,
            iconResource: DrawableResource? = null,
            action: HudAction? = null,
        ): CustomSnackbar? {
            val parentViewGroup = contentView.findSuitableParent() ?: return null
            var customSnackbar: CustomSnackbar? = null
            val composeView = ComposeView(contentView.context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    ComposeAppTheme {
                        AppSnackbar(
                            text = text,
                            variant = variant,
                            icon = iconRes?.let { painterResource(it) } ?: iconResource?.let { painterResource(it) },
                            modifier = Modifier.padding(8.dp),
                            action = action?.let {
                                HudAction(it.label) {
                                    it.onClick()
                                    customSnackbar?.dismiss()
                                }
                            },
                        )
                    }
                }
            }

            val snackbar = CustomSnackbar(parentViewGroup, composeView, ContentViewCallback(composeView))
            customSnackbar = snackbar
            snackbar.duration = duration.value
            snackbar.animationMode = ANIMATION_MODE_FADE

            if (gravity == SnackbarGravity.TOP_OF_VIEW)
                snackbar.anchorView = contentView

            return snackbar
        }
    }
}

internal fun View?.findSuitableParent(): ViewGroup? {
    var view = this
    var fallback: ViewGroup? = null
    do {
        if (view is CoordinatorLayout) {
            return view
        } else if (view is FrameLayout) {
            if (view.id == android.R.id.content) {
                return view
            } else {
                fallback = view
            }
        }

        if (view != null) {
            val parent = view.parent
            view = if (parent is View) parent else null
        }
    } while (view != null)

    return fallback
}

