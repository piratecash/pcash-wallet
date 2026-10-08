package cash.p.terminal.modules.nft.send

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.entities.nft.EvmNftRecord
import cash.p.terminal.entities.nft.NftKey
import cash.p.terminal.entities.nft.NftUid
import cash.p.terminal.modules.address.AddressInputModule
import cash.p.terminal.modules.address.AddressParserViewModel
import cash.p.terminal.modules.address.AddressViewModel
import cash.p.terminal.modules.send.evm.SendEvmAddressService
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui.compose.components.ScreenMessageWithAction
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.nftkit.models.NftType
import kotlinx.parcelize.Parcelize

class SendNftPage(val input: Input) : HSPage() {

    @Parcelize
    data class Input(val nftUid: String) : Parcelable

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        SendNftContent(navigation, input.nftUid)
    }

    @Composable
    private fun SendNftContent(navigation: HSNavigation, nftUid: String) {
        val factory = getFactory(nftUid)

        when (factory?.evmNftRecord?.nftType) {
            NftType.Eip721 -> {
                val eip721ViewModel = viewModel<SendEip721ViewModel>(factory = factory)
                val addressViewModel = viewModel<AddressViewModel>(
                    factory = AddressInputModule.FactoryNft(factory.nftUid.blockchainType)
                )
                val addressParserViewModel = viewModel<AddressParserViewModel>(factory = factory)
                SendEip721Screen(
                    navigation = navigation,
                    viewModel = eip721ViewModel,
                    addressViewModel = addressViewModel,
                    addressParserViewModel = addressParserViewModel,
                    sendEntryPoint = SendNftPage::class
                )
            }

            NftType.Eip1155 -> {
                val eip1155ViewModel = viewModel<SendEip1155ViewModel>(factory = factory)
                val addressViewModel = viewModel<AddressViewModel>(
                    factory = AddressInputModule.FactoryNft(factory.nftUid.blockchainType)
                )
                val addressParserViewModel = viewModel<AddressParserViewModel>(factory = factory)
                SendEip1155Screen(
                    navigation = navigation,
                    viewModel = eip1155ViewModel,
                    addressViewModel = addressViewModel,
                    addressParserViewModel = addressParserViewModel,
                    sendEntryPoint = SendNftPage::class
                )
            }

            else -> {
                ShowErrorMessage(navigation)
            }
        }
    }

}

private fun getFactory(nftUidString: String): SendNftModule.Factory? {
    val nftUid = NftUid.fromUid(nftUidString)

    val account = App.accountManager.activeAccount ?: return null

    if (account.isWatchAccount) return null

    val nftKey = NftKey(account, nftUid.blockchainType)

    val adapter = App.nftAdapterManager.adapter(nftKey) ?: return null

    val nftRecord = adapter.nftRecord(nftUid) ?: return null

    val evmNftRecord = (nftRecord as? EvmNftRecord) ?: return null

    return SendNftModule.Factory(
        evmNftRecord,
        nftUid,
        nftRecord.balance,
        adapter,
        SendEvmAddressService(),
        App.nftMetadataManager
    )
}

@Composable
private fun ShowErrorMessage(navigation: HSNavigation) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.SendNft_Title),
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(R.string.Button_Close),
                        icon = R.drawable.ic_close_24,
                        onClick = { navigation.navigateUpSafely() }
                    )
                )
            )
        }
    ) {
        Column(Modifier.padding(it)) {
            ScreenMessageWithAction(
                text = stringResource(R.string.Error),
                icon = R.drawable.ic_error_48
            )
        }
    }
}
