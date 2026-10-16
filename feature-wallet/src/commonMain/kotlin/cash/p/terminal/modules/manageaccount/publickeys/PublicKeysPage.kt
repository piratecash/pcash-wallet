package cash.p.terminal.modules.manageaccount.publickeys

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
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
import cash.p.terminal.modules.manageaccount.evmaddress.PublicViewKeyPage
import cash.p.terminal.modules.manageaccount.publickeys.PublicKeysModule.ZcashViewKeyKind
import cash.p.terminal.modules.manageaccount.showextendedkey.ShowExtendedKeyPage
import cash.p.terminal.modules.manageaccount.ui.KeyActionItem
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.PublicKeys_AccountExtendedPublicKey
import cash.p.terminal.resources.PublicKeys_AccountExtendedPublicKeyDescription
import cash.p.terminal.resources.PublicKeys_EvmAddress
import cash.p.terminal.resources.PublicKeys_EvmAddress_Description
import cash.p.terminal.resources.PublicKeys_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.publicKeys_zec_transparent_viewing_key
import cash.p.terminal.resources.publicKeys_zec_transparent_viewing_key_description
import cash.p.terminal.resources.publicKeys_zec_ufvk
import cash.p.terminal.resources.publicKeys_zec_ufvk_descritpion
import cash.p.terminal.resources.public_keys_zec_sapling_viewing_key
import cash.p.terminal.resources.public_keys_zec_sapling_viewing_key_description
import cash.p.terminal.resources.public_keys_zec_viewing_key_error

class PublicKeysPage(val input: Account) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ManageAccountScreen(navigation, input)
    }

}

@Composable
fun ManageAccountScreen(navigation: HSNavigation, account: Account) {
    val viewModel = koinViewModel<PublicKeysViewModel> { parametersOf(account) }
    val hudMessenger = rememberHudMessenger()

    LaunchedEffect(viewModel.viewState.zcashViewKeyFailed) {
        if (viewModel.viewState.zcashViewKeyFailed) {
            hudMessenger.showError(getString(Res.string.public_keys_zec_viewing_key_error))
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.PublicKeys_Title),
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
            viewModel.viewState.evmAddress?.let { evmAddress ->
                KeyActionItem(
                    title = stringResource(Res.string.PublicKeys_EvmAddress),
                    description = stringResource(Res.string.PublicKeys_EvmAddress_Description)
                ) {
                    navigation.slideFromRight(
                        PublicViewKeyPage(
                            PublicViewKeyPage.Input(
                                titleResId = Res.string.PublicKeys_EvmAddress,
                                viewKey = evmAddress,
                                showInfo = true
                            )
                        )
                    )
                }
            }
            viewModel.viewState.extendedPublicKey?.let { publicKey ->
                KeyActionItem(
                    title = stringResource(Res.string.PublicKeys_AccountExtendedPublicKey),
                    description = stringResource(Res.string.PublicKeys_AccountExtendedPublicKeyDescription),
                ) {
                    navigation.slideFromRight(
                        ShowExtendedKeyPage(
                            ShowExtendedKeyPage.Input(
                                publicKey.hdKey,
                                publicKey.accountPublicKey
                            )
                        )
                    )
                }
            }
            viewModel.viewState.zcashViewKey?.let { viewKey ->
                val (titleResId, descriptionResId) = zcashViewKeyLabels(viewKey.kind)
                KeyActionItem(
                    title = stringResource(titleResId),
                    description = stringResource(descriptionResId),
                ) {
                    navigation.slideFromRight(
                        PublicViewKeyPage(
                            PublicViewKeyPage.Input(
                                titleResId = titleResId,
                                viewKey = viewKey.key,
                                showInfo = false
                            )
                        )
                    )
                }
            }
        }
    }
}

internal fun zcashViewKeyLabels(kind: ZcashViewKeyKind): Pair<StringResource, StringResource> = when (kind) {
    ZcashViewKeyKind.Unified ->
        Res.string.publicKeys_zec_ufvk to Res.string.publicKeys_zec_ufvk_descritpion

    ZcashViewKeyKind.Sapling ->
        Res.string.public_keys_zec_sapling_viewing_key to
                Res.string.public_keys_zec_sapling_viewing_key_description

    ZcashViewKeyKind.Transparent ->
        Res.string.publicKeys_zec_transparent_viewing_key to
                Res.string.publicKeys_zec_transparent_viewing_key_description
}
