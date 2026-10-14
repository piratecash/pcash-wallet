package cash.p.terminal.ui_compose

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun BottomSheetHeader_Preview() {
    val iconPainter = painterResource(R.drawable.icon_24_lock)
    ComposeAppTheme {
        BottomSheetHeader(
            iconPainter = iconPainter,
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.brandDefault),
            title = stringResource(R.string.ManageAccount_SwitchWallet_Title),
            onCloseClick = { },
        ) {
            body_grey(
                modifier = Modifier.padding(horizontal = 24.dp),
                text = "Bottom sheet content",
                maxLines = 1,
            )
        }
    }
}
