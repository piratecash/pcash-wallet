package cash.p.terminal.modules.manageaccount.privatekeys

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.adapters.zcash.ZcashPrivateKeyType
import cash.p.terminal.feature.wallet.authorizeSecretAction
import cash.p.terminal.modules.manageaccount.generalprivatekey.GeneralPrivateKeyPage
import cash.p.terminal.modules.manageaccount.zcashkeys.ZcashKeysScreen
import cash.p.terminal.modules.manageaccount.zcashkeys.ZcashKeysViewModel
import cash.p.terminal.modules.manageaccount.zcashkeys.titleFor
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.PageResumeEffect
import cash.p.terminal.navigation.navigateUpFrom

import cash.p.terminal.wallet.Account
import cash.p.terminal.modules.manageaccount.evmprivatekey.EvmPrivateKeyPage
import cash.p.terminal.modules.manageaccount.showextendedkey.ShowExtendedKeyPage
import cash.p.terminal.modules.manageaccount.stellarsecretkey.StellarSecretKeyPage
import cash.p.terminal.modules.manageaccount.ui.KeyActionItem
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.navigation.navigateUpSafely
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.resources.PrivateKeys_AccountExtendedPrivateKey
import cash.p.terminal.resources.PrivateKeys_AccountExtendedPrivateKeyDescription
import cash.p.terminal.resources.PrivateKeys_Bip32RootKey
import cash.p.terminal.resources.PrivateKeys_Bip32RootKeyDescription
import cash.p.terminal.resources.PrivateKeys_EvmPrivateKey
import cash.p.terminal.resources.PrivateKeys_EvmPrivateKeyDescription
import cash.p.terminal.resources.PrivateKeys_StellarSecretKey
import cash.p.terminal.resources.PrivateKeys_StellarSecretKeyDescription
import cash.p.terminal.resources.PrivateKeys_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.private_keys_zec
import cash.p.terminal.resources.private_keys_zec_description

class PrivateKeysPage(val input: Account) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ManageAccountScreen(
            navigation = navigation,
            account = input,
            onZcashKeysClick = { navigation.slideFromRight(ZcashKeysPage(input)) },
        )
    }

}

internal class ZcashKeysPage(private val account: Account) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<ZcashKeysViewModel> { parametersOf(account.id) }
        val uiState = viewModel.uiState
        val transparentTitle = titleFor(ZcashPrivateKeyType.Transparent)
        val shieldedTitle = titleFor(ZcashPrivateKeyType.Shielded)

        LaunchedEffect(uiState.closeScreen) {
            if (uiState.closeScreen) navigation.navigateUpFrom(this@ZcashKeysPage)
        }
        PageResumeEffect(onResume = {}, onPause = viewModel::cancelReveal)

        ZcashKeysScreen(
            uiState = uiState,
            onReveal = { type ->
                navigation.authorizeSecretAction { viewModel.reveal(type) }
            },
            onShowKey = {
                uiState.revealed?.let { revealed ->
                    val title = when (revealed.type) {
                        ZcashPrivateKeyType.Transparent -> transparentTitle
                        ZcashPrivateKeyType.Shielded -> shieldedTitle
                    }
                    navigation.slideFromRight(
                        GeneralPrivateKeyPage(GeneralPrivateKeyPage.Input(revealed.key, title))
                    )
                }
                viewModel.onKeyShown()
            },
            onDismissError = viewModel::onErrorShown,
            onClose = navigation::navigateUpSafely,
        )
    }
}

@Composable
fun ManageAccountScreen(
    navigation: HSNavigation,
    account: Account,
    onZcashKeysClick: () -> Unit,
) {
    val viewModel = koinViewModel<PrivateKeysViewModel> { parametersOf(account) }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.PrivateKeys_Title),
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
                    title = stringResource(Res.string.PrivateKeys_EvmPrivateKey),
                    description = stringResource(Res.string.PrivateKeys_EvmPrivateKeyDescription)
                ) {
                    navigation.authorizeSecretAction {
                        navigation.slideFromRight(
                            EvmPrivateKeyPage(EvmPrivateKeyPage.Input(key))
                        )
                    }
                }
            }
            viewModel.viewState.stellarSecretKey?.let { key ->
                KeyActionItem(
                    title = stringResource(Res.string.PrivateKeys_StellarSecretKey),
                    description = stringResource(Res.string.PrivateKeys_StellarSecretKeyDescription)
                ) {
                    navigation.authorizeSecretAction {
                        navigation.slideFromRight(
                            StellarSecretKeyPage(StellarSecretKeyPage.Input(key))
                        )
                    }
                }
            }
            viewModel.viewState.bip32RootKey?.let { key ->
                KeyActionItem(
                    title = stringResource(Res.string.PrivateKeys_Bip32RootKey),
                    description = stringResource(Res.string.PrivateKeys_Bip32RootKeyDescription),
                ) {
                    navigation.authorizeSecretAction {
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
                    title = stringResource(Res.string.PrivateKeys_AccountExtendedPrivateKey),
                    description = stringResource(Res.string.PrivateKeys_AccountExtendedPrivateKeyDescription),
                ) {
                    navigation.authorizeSecretAction {
                        navigation.slideFromRight(
                            ShowExtendedKeyPage(ShowExtendedKeyPage.Input(key.hdKey, key.displayKeyType))
                        )
                    }
                }
            }
            if (viewModel.viewState.hasZcashKeys) {
                KeyActionItem(
                    title = stringResource(Res.string.private_keys_zec),
                    description = stringResource(Res.string.private_keys_zec_description),
                    onClick = onZcashKeysClick,
                )
            }
        }
    }
}
