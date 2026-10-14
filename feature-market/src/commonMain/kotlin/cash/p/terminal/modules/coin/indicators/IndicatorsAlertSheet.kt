package cash.p.terminal.modules.coin.indicators

import cash.p.terminal.resources.Button_LearnMore
import cash.p.terminal.resources.CoinPage_Indicators
import cash.p.terminal.resources.CoinPage_IndicatorsAlertText
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.icon_24_lock
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
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
            iconPainter = painterResource(Res.drawable.icon_24_lock),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.iconSecondary),
            title = stringResource(Res.string.CoinPage_Indicators),
            onCloseClick = onCloseClick
        ) {
            InfoText(
                text = stringResource(Res.string.CoinPage_IndicatorsAlertText)
            )
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                title = stringResource(Res.string.Button_LearnMore),
                onClick = {

                },
            )
            VSpacer(32.dp)
        }
    }
}
