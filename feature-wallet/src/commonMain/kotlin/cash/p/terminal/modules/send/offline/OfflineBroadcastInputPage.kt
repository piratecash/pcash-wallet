package cash.p.terminal.modules.send.offline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Button_Continue
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.offline_broadcast_input_hint
import cash.p.terminal.resources.offline_broadcast_title
import cash.p.terminal.ui.compose.components.FormsInputMultiline
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ButtonsGroupWithShade
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource

/** Where there is no camera: the signed transaction is pasted instead of scanned. */
class OfflineBroadcastInputPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        OfflineBroadcastInputScreen(
            onContinue = { text ->
                // Leaves the stack like the Android scanner, so Done returns to Settings.
                if (navigation.navigateUpFrom(this)) {
                    navigation.slideFromRight(OfflineBroadcastPage(OfflineBroadcastPage.Input(initialInput = text)))
                }
            },
            onBack = navigation::navigateUpSafely,
        )
    }
}

@Composable
internal fun OfflineBroadcastInputScreen(
    onContinue: (String) -> Unit,
    onBack: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.offline_broadcast_title),
                navigationIcon = { HsBackButton(onClick = onBack) },
            )
        },
        bottomBar = {
            ButtonsGroupWithShade {
                ButtonPrimaryYellow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    title = stringResource(Res.string.Button_Continue),
                    enabled = text.isNotBlank(),
                    onClick = { onContinue(text) },
                )
            }
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            VSpacer(12.dp)
            FormsInputMultiline(
                modifier = Modifier.padding(horizontal = 16.dp),
                initial = text,
                hint = stringResource(Res.string.offline_broadcast_input_hint),
                onValueChange = { text = it },
            )
        }
    }
}
