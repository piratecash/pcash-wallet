package cash.p.terminal.modules.fee

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Swap_Recipient
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource

@Preview
@Composable
private fun QuoteInfoRowPreview() {
    ComposeAppTheme {
        QuoteInfoRow(
            title = {
                subhead2_grey(text = stringResource(Res.string.Swap_Recipient))
            },
            value = {
                subhead2_leah(
                    text = "0x7A04536a50d12952f69E071e4c92693939db86b5",
                    textAlign = TextAlign.End
                )
            }
        )
    }
}
