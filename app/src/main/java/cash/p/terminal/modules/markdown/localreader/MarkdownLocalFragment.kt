package cash.p.terminal.modules.markdown.localreader

import android.os.Parcelable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import cash.p.terminal.R
import cash.p.terminal.modules.markdown.MarkdownContent
import cash.p.terminal.modules.markdown.openMarkdownOrWeblink
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.getInput
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.parcelize.Parcelize
import org.koin.androidx.viewmodel.ext.android.viewModel

class MarkdownLocalFragment : BaseComposeFragment() {
    private val viewModel: MarkdownLocalViewModel by viewModel()

    @Composable
    override fun GetContent(navController: NavController) {
        val input = navController.getInput<Input>()
        LaunchedEffect(Unit) {
            when (input) {
                is Input.Resource -> viewModel.parseContent(getString(input.resId))
                is Input.Asset -> viewModel.loadAsset(input.prefix)
                null -> Unit
            }
        }

        MarkdownLocalScreen(
            showAsPopup = input?.showAsPopup ?: false,
            viewModel = viewModel,
            onCloseClick = navController::popBackStackSafely,
            onUrlClick = { url ->
                navController.openMarkdownOrWeblink(url)
            }
        )
    }

    sealed interface Input : Parcelable {
        val showAsPopup: Boolean

        @Parcelize
        data class Resource(
            val resId: Int,
            override val showAsPopup: Boolean = false,
        ) : Input

        @Parcelize
        data class Asset(
            val prefix: String,
            override val showAsPopup: Boolean = false,
        ) : Input
    }
}

@Composable
private fun MarkdownLocalScreen(
    showAsPopup: Boolean,
    viewModel: MarkdownLocalViewModel,
    onCloseClick: () -> Unit,
    onUrlClick: (String) -> Unit,
) {

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            if (showAsPopup) {
                AppBar(
                    menuItems = listOf(
                        MenuItem(
                            title = TranslatableString.ResString(R.string.Button_Close),
                            icon = R.drawable.ic_close_24,
                            onClick = onCloseClick
                        )
                    )
                )
            } else {
                AppBar(navigationIcon = { HsBackButton(onClick = onCloseClick) })
            }
        }
    ) {
        MarkdownContent(
            modifier = Modifier.padding(it),
            viewState = viewModel.viewState,
            markdownContent = viewModel.markdownContent,
            addFooter = true,
            onRetryClick = {},
            onUrlClick = onUrlClick
        )
    }
}
