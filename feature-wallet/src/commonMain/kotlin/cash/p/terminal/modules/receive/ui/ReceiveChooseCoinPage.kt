package cash.p.terminal.modules.receive.ui

import androidx.compose.runtime.remember
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.components.showNotSupportedOnPlatform
import cash.p.terminal.wallet.policy.PlatformChainSupport
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.modules.receive.ReceivePage
import cash.p.terminal.modules.receive.viewmodels.BchAddressTypeSelectViewModel
import cash.p.terminal.modules.receive.viewmodels.DerivationSelectViewModel
import cash.p.terminal.modules.receive.viewmodels.ReceiveSharedViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.viewModelStoreOwnerForPage
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Balance_Receive_AddressFormat_RecommendedAddressType
import cash.p.terminal.resources.Balance_Receive_AddressFormat_RecommendedDerivation
import cash.p.terminal.resources.Error_ParameterNotSet

class ReceiveChooseCoinPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.receiveSharedViewModel()
        val activeAccount = viewModel.activeAccount
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
            onCoinClick = navigation.rememberOnSelectWallet(),
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
        val bchAddressViewModel = koinViewModel<BchAddressTypeSelectViewModel> { parametersOf(coinUid) }
        AddressFormatSelectScreen(
            addressFormatItems = bchAddressViewModel.items,
            description = stringResource(Res.string.Balance_Receive_AddressFormat_RecommendedAddressType),
            onSelect = navigation.rememberOnSelectWallet(),
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
        val derivationViewModel = koinViewModel<DerivationSelectViewModel> { parametersOf(coinUid) }
        AddressFormatSelectScreen(
            addressFormatItems = derivationViewModel.items,
            description = stringResource(Res.string.Balance_Receive_AddressFormat_RecommendedDerivation),
            onSelect = navigation.rememberOnSelectWallet(),
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
            onSelect = navigation.rememberOnSelectWallet()
        )
    }
}

@Composable
private fun HSNavigation.receiveSharedViewModel(): ReceiveSharedViewModel =
    koinViewModel(viewModelStoreOwner = viewModelStoreOwnerForPage(ReceiveChooseCoinPage::class))

@Composable
private fun HSNavigation.rememberOnSelectWallet(): (Wallet) -> Unit {
    val chainSupport = koinInject<PlatformChainSupport>()
    val hud = rememberHudMessenger()
    return remember(this, chainSupport, hud) {
        { wallet ->
            if (chainSupport.isSupported(wallet)) {
                slideFromRight(ReceivePage(ReceivePage.Input(wallet, receiveEntryPoint = ReceiveChooseCoinPage::class)))
            } else {
                hud.showNotSupportedOnPlatform()
            }
        }
    }
}

@Composable
private fun CloseWithMessage(navigation: HSNavigation) {
    val hud = rememberHudMessenger()
    val message = stringResource(Res.string.Error_ParameterNotSet)
    LaunchedEffect(Unit) {
        hud.showError(message)
        navigation.removeLastUntil(ReceiveChooseCoinPage::class, true)
    }
}
