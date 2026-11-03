package cash.p.terminal.modules.send

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
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.components.ButtonPrimaryRed
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.Button_ContinueAnyway
import cash.p.terminal.resources.Send_RiskyAddress
import cash.p.terminal.resources.ic_attention_24

class AddressRiskySheet(val input: Input) : HSBottomSheet() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        RiskyAddressAlertView(
            alertText = input.alertText,
            onCloseClick = navigation::navigateUpSafely,
            onContinueClick = {
                navigation.setResult(this, Result(true))
            }
        )
    }

    data class Input(val alertText: String)

    data class Result(val canContinue: Boolean)
}

@Composable
private fun RiskyAddressAlertView(
    alertText: String,
    onCloseClick: () -> Unit,
    onContinueClick: () -> Unit,
) {
    ComposeAppTheme {
        BottomSheetHeader(
            iconPainter = painterResource(Res.drawable.ic_attention_24),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.statusError),
            title = stringResource(Res.string.Send_RiskyAddress),
            onCloseClick = onCloseClick
        ) {
            VSpacer(12.dp)
            TextImportantError(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = alertText
            )
            VSpacer(32.dp)
            ButtonPrimaryRed(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(Res.string.Button_ContinueAnyway),
                onClick = {
                    onContinueClick()
                }
            )
            VSpacer(12.dp)
            ButtonPrimaryTransparent(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(Res.string.Button_Cancel),
                onClick = onCloseClick
            )
            VSpacer(32.dp)
        }
    }
}
