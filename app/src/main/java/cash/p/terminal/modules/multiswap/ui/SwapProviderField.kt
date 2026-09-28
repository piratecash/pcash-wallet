package cash.p.terminal.modules.multiswap.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.ui.compose.components.HSRow
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsImage
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.components.CellUniversal

@Composable
fun SwapProviderField(
    title: String,
    @DrawableRes iconId: Int,
    iconUrl: String? = null,
) {
    CellUniversal(borderTop = true) {
        HSRow(
            verticalAlignment = Alignment.CenterVertically
        ) {
            subhead2_grey(text = stringResource(R.string.Swap_SwapProvider_Title))
            Spacer(Modifier.weight(1f))
            HsImage(
                url = iconUrl,
                placeholder = iconId,
                modifier = Modifier.size(24.dp),
            )
            HSpacer(width = 8.dp)
            subhead1_leah(text = title)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SwapProviderFieldPreview() {
    ComposeAppTheme {
        SwapProviderField(
            title = "Uniswap",
            iconId = R.drawable.uniswap
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SwapProviderFieldRemoteIconPreview() {
    ComposeAppTheme {
        SwapProviderField(
            title = "Changelly",
            iconId = R.drawable.ic_changelly,
            iconUrl = "https://example.com/icon.png",
        )
    }
}
