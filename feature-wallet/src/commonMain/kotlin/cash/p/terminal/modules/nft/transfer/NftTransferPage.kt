package cash.p.terminal.modules.nft.transfer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.modules.address.AddressViewModel
import cash.p.terminal.modules.address.HSAddressInput
import cash.p.terminal.feature.wallet.rememberTransferAction
import cash.p.terminal.modules.address.NftTransferAddressQualifier
import cash.p.terminal.modules.nft.NftPreviewPage
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.nft_transfer_recipient_hint
import cash.p.terminal.resources.send_error_adapter_not_found
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class NftTransferPage(
    private val item: NftItemRecord,
    private val wallet: Wallet,
    private val domain: String?,
) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val adapter = remember { getKoinInstance<IAdapterManager>().getAdapterForWallet<ISendTonAdapter>(wallet) }
        if (adapter == null) {
            MissingAdapterEffect(navigation)
            return
        }
        val viewModel: NftTransferViewModel = viewModel(
            factory = remember { NftTransferViewModelFactory(item, wallet, adapter) }
        )
        TransferContent(navigation, viewModel, adapter)
    }

    @Composable
    private fun TransferContent(
        navigation: HSNavigation,
        viewModel: NftTransferViewModel,
        adapter: ISendTonAdapter,
    ) {
        val uiState = viewModel.uiState
        val openConfirmation = navigation.rememberTransferAction<Unit> {
            navigation.slideFromRight(NftTransferConfirmationPage(item))
        }
        // Covers Sent arriving after the user pressed Back to the form.
        val sent = viewModel.sendResult is SendResult.Sent
        LaunchedEffect(sent) {
            if (sent) navigation.removeLastUntil(NftPreviewPage::class, inclusive = true)
        }
        NftTransferScreen(
            item = item,
            nftDomain = domain,
            uiState = uiState,
            canContinue = viewModel.canContinue,
            recipientInput = { RecipientInput(navigation, adapter, uiState, viewModel::onEnterAddress) },
            actions = NftTransferScreenActions(
                onRiskAcceptedChange = viewModel::onRiskAcceptedChange,
                onCommentChange = viewModel::onEnterComment,
                onContinue = { openConfirmation(Unit) },
                onBack = navigation::navigateUpSafely,
            ),
        )
    }

    @Composable
    private fun RecipientInput(
        navigation: HSNavigation,
        adapter: ISendTonAdapter,
        uiState: NftTransferUiState,
        onAddressChange: (Address?) -> Unit,
    ) {
        val addressViewModel = koinViewModel<AddressViewModel>(qualifier = NftTransferAddressQualifier) {
            parametersOf(adapter)
        }
        HSAddressInput(
            viewModel = addressViewModel,
            inputState = addressViewModel.inputState.collectAsStateWithLifecycle().value,
            address = addressViewModel.address.collectAsStateWithLifecycle().value,
            value = addressViewModel.value.collectAsStateWithLifecycle().value,
            error = uiState.addressError,
            navigation = navigation,
            onValueChange = onAddressChange,
            isPoisonAddress = uiState.isPoisonAddress,
            hint = stringResource(Res.string.nft_transfer_recipient_hint),
        )
    }

    @Composable
    private fun MissingAdapterEffect(navigation: HSNavigation) {
        val hud = rememberHudMessenger()
        val message = stringResource(Res.string.send_error_adapter_not_found, wallet.coin.code)
        LaunchedEffect(Unit) {
            hud.showError(message)
            navigation.navigateUpFrom(this@NftTransferPage)
        }
    }
}
