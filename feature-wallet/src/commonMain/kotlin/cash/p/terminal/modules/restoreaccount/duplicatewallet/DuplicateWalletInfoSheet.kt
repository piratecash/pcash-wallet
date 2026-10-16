package cash.p.terminal.modules.restoreaccount.duplicatewallet

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.duplicate_wallet
import cash.p.terminal.resources.duplicate_wallet_info
import cash.p.terminal.resources.ic_copy_24px

internal class DuplicateWalletInfoSheet : HSBottomSheet() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ComposeAppTheme {
            DuplicateWalletInfoScreen(navigation)
        }
    }
}

@Composable
internal fun DuplicateWalletInfoScreen(navigation: HSNavigation) {
    BottomSheetHeader(
        iconPainter = painterResource(Res.drawable.ic_copy_24px),
        title = stringResource(Res.string.duplicate_wallet),
        onCloseClick = {
            navigation.navigateUpSafely()
        }
    ) {
        body_leah(
            text = stringResource(Res.string.duplicate_wallet_info),
            modifier = Modifier
                .padding(16.dp)
                .border(1.dp, ComposeAppTheme.colors.borderDefault,
                    RoundedCornerShape(12.dp))
                .padding(16.dp)
        )
        Spacer(Modifier.height(8.dp))
    }
}
