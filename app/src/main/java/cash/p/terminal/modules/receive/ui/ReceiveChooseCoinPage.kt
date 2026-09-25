package cash.p.terminal.modules.receive.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.modules.receive.ReceivePage
import cash.p.terminal.modules.receive.viewmodels.BchAddressTypeSelectViewModel
import cash.p.terminal.modules.receive.viewmodels.DerivationSelectViewModel
import cash.p.terminal.modules.receive.viewmodels.ReceiveSharedViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.wallet.Wallet

class ReceiveChooseCoinPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.receiveSharedViewModel()
        val activeAccount = App.accountManager.activeAccount
        if (activeAccount == null) {
            CloseWithMessage(navigation)
            return
        }
        ReceiveTokenSelectScreen(
            activeAccount = activeAccount,
            onMultipleAddressesClick = { coinUid ->
                viewModel.coinUid = coinUid
                navigation.slideFromRight(BchAddressFormatPage())
            },
            onMultipleDerivationsClick = { coinUid ->
                viewModel.coinUid = coinUid
                navigation.slideFromRight(DerivationSelectPage())
            },
            onMultipleBlockchainsClick = { coinUid ->
                viewModel.coinUid = coinUid
                navigation.slideFromRight(NetworkSelectPage())
            },
            onCoinClick = navigation::onSelectWallet,
            onBackPress = navigation::navigateUpSafely,
        )
    }
}

class BchAddressFormatPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val coinUid = navigation.receiveSharedViewModel().coinUid
        if (coinUid == null) {
            CloseWithMessage(navigation)
            return
        }
        val bchAddressViewModel = viewModel<BchAddressTypeSelectViewModel>(
            factory = BchAddressTypeSelectViewModel.Factory(coinUid)
        )
        AddressFormatSelectScreen(
            addressFormatItems = bchAddressViewModel.items,
            description = stringResource(R.string.Balance_Receive_AddressFormat_RecommendedAddressType),
            onSelect = navigation::onSelectWallet,
            onBackPress = navigation::navigateUpSafely
        )
    }
}

class DerivationSelectPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val coinUid = navigation.receiveSharedViewModel().coinUid
        if (coinUid == null) {
            CloseWithMessage(navigation)
            return
        }
        val derivationViewModel = viewModel<DerivationSelectViewModel>(
            factory = DerivationSelectViewModel.Factory(coinUid)
        )
        AddressFormatSelectScreen(
            addressFormatItems = derivationViewModel.items,
            description = stringResource(R.string.Balance_Receive_AddressFormat_RecommendedDerivation),
            onSelect = navigation::onSelectWallet,
            onBackPress = navigation::navigateUpSafely
        )
    }
}

class NetworkSelectPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.receiveSharedViewModel()
        val activeAccount = viewModel.activeAccount
        val fullCoin = viewModel.fullCoin()
        if (activeAccount == null || fullCoin == null) {
            CloseWithMessage(navigation)
            return
        }
        NetworkSelectScreen(
            navigation = navigation,
            activeAccount = activeAccount,
            fullCoin = fullCoin,
            onSelect = navigation::onSelectWallet
        )
    }
}

@Composable
private fun HSNavigation.receiveSharedViewModel(): ReceiveSharedViewModel =
    viewModel(viewModelStoreOwner = viewModelStoreOwnerForPage(ReceiveChooseCoinPage::class))

private fun HSNavigation.onSelectWallet(wallet: Wallet) {
    slideFromRight(ReceivePage(ReceivePage.Input(wallet, receiveEntryPoint = ReceiveChooseCoinPage::class)))
}

@Composable
private fun CloseWithMessage(navigation: HSNavigation) {
    val view = LocalView.current
    val message = stringResource(id = R.string.Error_ParameterNotSet)
    LaunchedEffect(Unit) {
        HudHelper.showErrorMessage(view, message)
        navigation.removeLastUntil(ReceiveChooseCoinPage::class, true)
    }
}
