package cash.p.terminal.ui.dialogs

import android.os.Parcelable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.InfoTextBody
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import kotlinx.parcelize.Parcelize

class CancelOrScanSheet : HSBottomSheet() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val accountManager = remember { getKoinInstance<IAccountManager>() }
        CancelOrScanScreen(
            accountType = accountManager.activeAccount?.type,
            onClose = navigation::navigateUpSafely,
            onConfirm = {
                navigation.setResult(this, Result(true))
                navigation.navigateUpSafely()
            },
            onCancel = {
                navigation.setResult(this, Result(false))
                navigation.navigateUpSafely()
            }
        )
    }

    @Parcelize
    data class Result(val confirmed: Boolean) : Parcelable
}

@Composable
private fun CancelOrScanScreen(
    accountType: AccountType?,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val isTrezor = accountType is AccountType.TrezorDevice
    val bodyText = stringResource(
        if (isTrezor) R.string.connect_trezor_to_add_message else R.string.scan_to_add_message
    )
    val confirmText = stringResource(
        if (isTrezor) R.string.connect else R.string.scan
    )

    ComposeAppTheme {
        BottomSheetHeader(
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.lucian),
            title = stringResource(R.string.adding_tokens),
            onCloseClick = onClose
        ) {
            InfoTextBody(text = bodyText)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ButtonPrimaryYellow(
                    modifier = Modifier
                        .weight(1f),
                    title = confirmText,
                    onClick = onConfirm
                )
                ButtonPrimaryDefault(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.common_cancel),
                    onClick = onCancel
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
