package cash.p.terminal.modules.walletconnect.pairing

import android.os.Parcelable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import cash.p.terminal.ui_compose.components.ButtonPrimaryRed
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.parcelize.Parcelize

class ConfirmDeleteAllPairingsSheet : HSBottomSheet() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ConfirmDeleteAllScreen(
            navigation = navigation,
            onDeleteClick = {
                navigation.setResult(this, Result(true))
                navigation.navigateUpSafely()
            }
        )
    }

    @Parcelize
    data class Result(val confirmed: Boolean) : Parcelable
}

@Composable
fun ConfirmDeleteAllScreen(navigation: HSNavigation, onDeleteClick: () -> Unit) {
    ComposeAppTheme {
        BottomSheetHeader(
            iconPainter = painterResource(R.drawable.ic_delete_20),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.lucian),
            title = stringResource(R.string.WalletConnect_DeleteAllPairs),
            onCloseClick = {
                navigation.navigateUpSafely()
            }
        ) {
            TextImportantWarning(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                text = stringResource(R.string.WalletConnect_Pairings_ConfirmationDeleteAll)
            )
            Spacer(Modifier.height(20.dp))
            ButtonPrimaryRed(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(R.string.WalletConnect_Pairings_Delete),
                onClick = onDeleteClick
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
