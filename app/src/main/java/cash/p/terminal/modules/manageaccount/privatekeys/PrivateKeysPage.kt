package cash.p.terminal.modules.manageaccount.privatekeys

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage

import cash.p.terminal.wallet.Account
import cash.p.terminal.modules.manageaccount.evmprivatekey.EvmPrivateKeyPage
import cash.p.terminal.modules.manageaccount.showextendedkey.ShowExtendedKeyPage
import cash.p.terminal.modules.manageaccount.stellarsecretkey.StellarSecretKeyPage
import cash.p.terminal.modules.manageaccount.ui.KeyActionItem
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.navigation.navigateUpSafely

class PrivateKeysPage(val input: Account) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ManageAccountScreen(navigation, input)
    }

}

@Composable
fun ManageAccountScreen(navigation: HSNavigation, account: Account) {
    val viewModel = viewModel<PrivateKeysViewModel>(factory = PrivateKeysModule.Factory(account))

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.PrivateKeys_Title),
                navigationIcon = {
                    HsBackButton(onClick = { navigation.navigateUpSafely() })
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))
            viewModel.viewState.evmPrivateKey?.let { key ->
                KeyActionItem(
                    title = stringResource(id = R.string.PrivateKeys_EvmPrivateKey),
                    description = stringResource(R.string.PrivateKeys_EvmPrivateKeyDescription)
                ) {
                    navigation.authorizedAction {
                        navigation.slideFromRight(
                            EvmPrivateKeyPage(EvmPrivateKeyPage.Input(key))
                        )
                    }
                }
            }
            viewModel.viewState.stellarSecretKey?.let { key ->
                KeyActionItem(
                    title = stringResource(id = R.string.PrivateKeys_StellarSecretKey),
                    description = stringResource(R.string.PrivateKeys_StellarSecretKeyDescription)
                ) {
                    navigation.authorizedAction {
                        navigation.slideFromRight(
                            StellarSecretKeyPage(StellarSecretKeyPage.Input(key))
                        )
                    }
                }
            }
            viewModel.viewState.bip32RootKey?.let { key ->
                KeyActionItem(
                    title = stringResource(id = R.string.PrivateKeys_Bip32RootKey),
                    description = stringResource(id = R.string.PrivateKeys_Bip32RootKeyDescription),
                ) {
                    navigation.authorizedAction {
                        navigation.slideFromRight(
                            ShowExtendedKeyPage(
                                ShowExtendedKeyPage.Input(
                                    key.hdKey,
                                    key.displayKeyType
                                )
                            )
                        )
                    }
                }
            }
            viewModel.viewState.accountExtendedPrivateKey?.let { key ->
                KeyActionItem(
                    title = stringResource(id = R.string.PrivateKeys_AccountExtendedPrivateKey),
                    description = stringResource(id = R.string.PrivateKeys_AccountExtendedPrivateKeyDescription),
                ) {
                    navigation.authorizedAction {
                        navigation.slideFromRight(
                            ShowExtendedKeyPage(ShowExtendedKeyPage.Input(key.hdKey, key.displayKeyType))
                        )
                    }
                }
            }
        }
    }
}
