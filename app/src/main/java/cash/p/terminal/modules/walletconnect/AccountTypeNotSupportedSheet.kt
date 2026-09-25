package cash.p.terminal.modules.walletconnect

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.R
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.manageaccounts.ManageAccountsPage
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class AccountTypeNotSupportedSheet(val input: Input) : HSBottomSheet() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<AccountTypeNotSupportedViewModel> {
            parametersOf(input)
        }
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        AccountTypeNotSupportedScreen(
            uiState = uiState,
            onCloseClick = {
                navigation.navigateUpSafely()
            },
            onSwitchClick = {
                navigation.navigateUpSafely()
                navigation.slideFromRight(ManageAccountsPage(ManageAccountsModule.Mode.Manage))
            }
        )
    }

    @Parcelize
    data class Input(
        @DrawableRes val iconResId: Int,
        @StringRes val titleResId: Int,
        val connectionLabel: String
    ) : Parcelable
}

@Composable
private fun AccountTypeNotSupportedScreen(
    uiState: AccountTypeNotSupportedUiState,
    onCloseClick: () -> Unit,
    onSwitchClick: () -> Unit
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler
    ) { innerPadding ->
        BottomSheetHeader(
            iconPainter = painterResource(uiState.iconResId),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
            title = stringResource(uiState.titleResId),
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            onCloseClick = onCloseClick
        ) {
            TextImportantWarning(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                text = stringResource(
                    id = R.string.account_not_supported_by,
                    uiState.accountTypeDescription,
                    uiState.connectionLabel
                )
            )
            ButtonPrimaryYellow(
                modifier = Modifier
                    .padding(vertical = 20.dp, horizontal = 24.dp)
                    .fillMaxWidth(),
                title = stringResource(R.string.Button_Switch),
                onClick = onSwitchClick
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Preview
@Composable
private fun AccountTypeNotSupportedScreenPreview() {
    ComposeAppTheme {
        AccountTypeNotSupportedScreen(
            uiState = AccountTypeNotSupportedUiState(
                iconResId = R.drawable.ic_wallet_connect_24,
                titleResId = R.string.WalletConnect_Title,
                accountTypeDescription = "EVM wallet",
                connectionLabel = "WalletConnect v2"
            ),
            onCloseClick = {},
            onSwitchClick = {}
        )
    }
}
