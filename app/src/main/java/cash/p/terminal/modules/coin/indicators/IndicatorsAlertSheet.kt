package cash.p.terminal.modules.coin.indicators

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

class IndicatorsAlertSheet : HSBottomSheet() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        IndicatorsAlertScreen { navigation.navigateUpSafely() }
    }
}

@Composable
private fun IndicatorsAlertScreen(onCloseClick: () -> Unit) {
    ComposeAppTheme {
        BottomSheetHeader(
            iconPainter = painterResource(R.drawable.icon_24_lock),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.grey),
            title = stringResource(R.string.CoinPage_Indicators),
            onCloseClick = onCloseClick
        ) {
            InfoText(
                text = stringResource(R.string.CoinPage_IndicatorsAlertText)
            )
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                title = stringResource(R.string.Button_LearnMore),
                onClick = {

                },
            )
            VSpacer(32.dp)
        }
    }
}
