package cash.p.terminal.tangem.ui.resetBackupDialog

import android.os.Parcelable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.tangem.R
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.parcelize.Parcelize

class ResetBackupSheet : HSBottomSheet() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ComposeAppTheme {
            ResetBackupScreen(
                onResetClick = {
                    navigation.setResult(this, Result(true))
                    navigation.navigateUpSafely()
                },
                onCloseClick = {
                    navigation.navigateUpSafely()
                }
            )
        }
    }

    @Parcelize
    internal data class Result(val confirmed: Boolean) : Parcelable
}


@Composable
private fun ResetBackupScreen(
    onResetClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    BottomSheetHeader(
        iconPainter = painterResource(R.drawable.ic_attention_24),
        iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
        title = stringResource(R.string.common_attention),
        onCloseClick = onCloseClick
    ) {

        InfoText(text = stringResource(R.string.onboarding_linking_error_card_with_wallets))
        VSpacer(24.dp)

        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
            title = stringResource(R.string.reset),
            onClick = onResetClick
        )
        VSpacer(8.dp)
        ButtonPrimaryDefault(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
            title = stringResource(R.string.Button_Cancel),
            onClick = onCloseClick
        )
        VSpacer(32.dp)
    }
}

@Composable
@Preview(showBackground = true)
private fun ResetBackupScreennPreview() {
    ComposeAppTheme {
        ResetBackupScreen(
            onResetClick = {},
            onCloseClick = {}
        )
    }
}
