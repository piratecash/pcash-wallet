package cash.p.terminal.modules.restoreaccount.duplicatewallet

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.R
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

internal class DuplicateWalletInfoSheet : HSBottomSheet() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ComposeAppTheme {
            DuplicateWalletInfoScreen(navigation)
        }
    }
}

@Composable
private fun DuplicateWalletInfoScreen(navigation: HSNavigation) {
    BottomSheetHeader(
        iconPainter = painterResource(R.drawable.ic_copy_24px),
        title = stringResource(R.string.duplicate_wallet),
        onCloseClick = {
            navigation.navigateUpSafely()
        }
    ) {
        body_leah(
            text = stringResource(R.string.duplicate_wallet_info),
            modifier = Modifier
                .padding(16.dp)
                .border(1.dp, ComposeAppTheme.colors.jeremy,
                    RoundedCornerShape(12.dp))
                .padding(16.dp)
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Preview(showBackground = true)
@Composable
private fun DuplicateWalletInfoScreenPreview() {
    ComposeAppTheme {
        DuplicateWalletInfoScreen(navigation = HSNavigation(NavBackStack()))
    }
}
