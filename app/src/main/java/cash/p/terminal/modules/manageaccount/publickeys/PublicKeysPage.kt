package cash.p.terminal.modules.manageaccount.publickeys

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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.modules.manageaccount.evmaddress.PublicViewKeyPage
import cash.p.terminal.modules.manageaccount.publickeys.PublicKeysModule.ZcashViewKeyKind
import cash.p.terminal.modules.manageaccount.showextendedkey.ShowExtendedKeyPage
import cash.p.terminal.modules.manageaccount.ui.KeyActionItem
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account
import cash.p.terminal.navigation.navigateUpSafely

class PublicKeysPage(val input: Account) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ManageAccountScreen(navigation, input)
    }

}

@Composable
fun ManageAccountScreen(navigation: HSNavigation, account: Account) {
    val viewModel = viewModel<PublicKeysViewModel>(factory = PublicKeysModule.Factory(account))
    val view = LocalView.current

    LaunchedEffect(viewModel.viewState.zcashViewKeyFailed) {
        if (viewModel.viewState.zcashViewKeyFailed) {
            HudHelper.showErrorMessage(view, R.string.public_keys_zec_viewing_key_error)
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.PublicKeys_Title),
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
                    title = stringResource(id = R.string.PublicKeys_EvmAddress),
                    description = stringResource(R.string.PublicKeys_EvmAddress_Description)
                ) {
                    navigation.slideFromRight(
                        PublicViewKeyPage(
                            PublicViewKeyPage.Input(
                                titleResId = R.string.PublicKeys_EvmAddress,
                                viewKey = evmAddress,
                                showInfo = true
                            )
                        )
                    )
                }
            }
            viewModel.viewState.extendedPublicKey?.let { publicKey ->
                KeyActionItem(
                    title = stringResource(id = R.string.PublicKeys_AccountExtendedPublicKey),
                    description = stringResource(id = R.string.PublicKeys_AccountExtendedPublicKeyDescription),
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
                    title = stringResource(id = titleResId),
                    description = stringResource(id = descriptionResId),
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

internal fun zcashViewKeyLabels(kind: ZcashViewKeyKind): Pair<Int, Int> = when (kind) {
    ZcashViewKeyKind.Unified ->
        R.string.publicKeys_zec_ufvk to R.string.publicKeys_zec_ufvk_descritpion

    ZcashViewKeyKind.Sapling ->
        R.string.public_keys_zec_sapling_viewing_key to
                R.string.public_keys_zec_sapling_viewing_key_description

    ZcashViewKeyKind.Transparent ->
        R.string.publicKeys_zec_transparent_viewing_key to
                R.string.publicKeys_zec_transparent_viewing_key_description
}
