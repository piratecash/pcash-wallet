package cash.p.terminal.ui_compose.components.hardware

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.caption_lucian
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

/** Connect step shared by the hardware wallet vendors: instructions, a connect button and its progress. */
@Composable
fun HardwareSetupScreen(
    title: String,
    description: String,
    connectButtonTitle: String,
    loading: Boolean,
    onConnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    statusText: String? = null,
    errorText: String? = null,
) {
    Column(modifier = modifier.background(color = ComposeAppTheme.colors.backgroundBase)) {
        AppBar(
            title = title,
            navigationIcon = {
                HsBackButton(onClick = onBack)
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            subhead2_grey(
                text = description,
                textAlign = TextAlign.Center,
                modifier = Modifier.centeredText()
            )
            Spacer(Modifier.height(32.dp))
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                title = connectButtonTitle,
                onClick = onConnect,
                enabled = !loading,
                loadingIndicator = loading,
            )
            statusText?.let {
                Spacer(Modifier.height(16.dp))
                subhead2_leah(text = it, textAlign = TextAlign.Center, modifier = Modifier.centeredText())
            }
            errorText?.let {
                Spacer(Modifier.height(16.dp))
                caption_lucian(text = it, textAlign = TextAlign.Center, modifier = Modifier.centeredText())
            }
        }
    }
}

private fun Modifier.centeredText() = fillMaxWidth().padding(horizontal = 32.dp)
