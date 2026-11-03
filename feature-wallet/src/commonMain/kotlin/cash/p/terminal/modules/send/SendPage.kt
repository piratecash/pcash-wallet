package cash.p.terminal.modules.send

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.core.adapters.BeamAdapter
import cash.p.terminal.core.ISendBitcoinAdapter
import cash.p.terminal.core.ISendEthereumAdapter
import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.core.ISendSolanaAdapter
import cash.p.terminal.core.ISendMoneroAdapter
import cash.p.terminal.core.ISendMemoAdapter
import cash.p.terminal.core.ISendTronAdapter
import cash.p.terminal.entities.Address
import cash.p.terminal.feature.wallet.rememberTransferAction
import cash.p.terminal.modules.amount.AmountInputModeViewModel
import cash.p.terminal.modules.send.SendConfirmationPage.Type
import cash.p.terminal.modules.send.address.AddressCheckerControl
import cash.p.terminal.modules.send.address.isSmartContractCheckSupported
import cash.p.terminal.modules.send.bitcoin.SendBitcoinScreen
import cash.p.terminal.modules.send.bitcoin.SendBitcoinViewModel
import cash.p.terminal.modules.send.bitcoin.advanced.BtcTransactionInputSortInfoScreen
import cash.p.terminal.modules.send.bitcoin.advanced.SendBtcAdvancedSettingsScreen
import cash.p.terminal.modules.send.bitcoin.utxoexpert.UtxoExpertModeScreen
import cash.p.terminal.modules.send.evm.SendEvmPageContent
import cash.p.terminal.modules.send.evm.SendEvmViewModel
import cash.p.terminal.modules.send.memo.SendMemoPageContent
import cash.p.terminal.modules.send.memo.SendMemoViewModel
import cash.p.terminal.modules.send.monero.SendMoneroPageContent
import cash.p.terminal.modules.send.monero.SendMoneroViewModel
import cash.p.terminal.modules.send.securitycheck.SecurityCheckPage
import cash.p.terminal.modules.send.solana.SendSolanaPageContent
import cash.p.terminal.modules.send.solana.SendSolanaViewModel
import cash.p.terminal.modules.send.ton.SendTonPageContent
import cash.p.terminal.modules.send.ton.SendTonViewModel
import cash.p.terminal.modules.send.tron.SendTronPageContent
import cash.p.terminal.modules.send.tron.SendTronViewModel
import cash.p.terminal.modules.send.zcash.SendZCashPageContent
import cash.p.terminal.modules.send.zcash.SendZCashViewModel
import cash.p.terminal.modules.sendtokenselect.PrefilledData
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.rememberResultLauncher
import io.horizontalsystems.core.Translator
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.modules.send.beam.BeamSendScreen
import cash.p.terminal.modules.send.beam.BeamSendViewModel
import cash.p.terminal.modules.send.beam.handleBeamProceed
import cash.p.terminal.modules.send.beam.isNativeBeamSendWallet
import org.koin.java.KoinJavaComponent.inject
import co.touchlab.kermit.Logger
import kotlin.reflect.KClass
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Send_RiskyAddress_AlertText
import cash.p.terminal.resources.send_error_adapter_not_found
import cash.p.terminal.resources.send_poison_address_alert
import cash.p.terminal.wallet.IAdapterManager
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val logger = Logger.withTag("SendPage")

class SendPage(val input: Input) : HSPage() {

    private val addressCheckerControl: AddressCheckerControl by inject(AddressCheckerControl::class.java)
    private val poisonAddressManager: PoisonAddressManager by lazy { getKoinInstance() }

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val keyboardController = LocalSoftwareKeyboardController.current
        val wallet = input.wallet
        val title = input.title
        val prefilledData = input.prefilledData
        val address = prefilledData?.address?.let(::Address)
        val hideAddress = input.hideAddress
        val amount = prefilledData?.amount

        val amountInputModeViewModel = koinViewModel<AmountInputModeViewModel> { parametersOf(wallet.coin.uid) }
        val openConfirm = navigation.rememberOpenConfirm(input.riskyAddress, input.sendEntryPoint, keyboardController)

        if (wallet.token.blockchainType == BlockchainType.Beam) {
            BeamContent(wallet, title, navigation, amountInputModeViewModel, openConfirm)
            return
        }

        when (wallet.token.blockchainType) {
            BlockchainType.Bitcoin,
            BlockchainType.BitcoinCash,
            BlockchainType.ECash,
            BlockchainType.Litecoin,
            BlockchainType.Dogecoin,
            BlockchainType.PirateCash,
            BlockchainType.Cosanta,
            BlockchainType.Dash -> {
                val adapter: ISendBitcoinAdapter? = getKoinInstance<IAdapterManager>().getAdapterForWallet(wallet)
                if (adapter == null) {
                    MissingWalletAdapterEffect(
                        navigation = navigation,
                        coinUid = wallet.coin.uid,
                        coinCode = wallet.coin.code,
                    )
                } else {
                    val sendBitcoinViewModel = koinViewModel<SendBitcoinViewModel> {
                        parametersOf(wallet, address, hideAddress, adapter)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                    ) {
                        SendBitcoinScreen(
                            title = title,
                            navigation = navigation,
                            viewModel = sendBitcoinViewModel,
                            amountInputModeViewModel = amountInputModeViewModel,
                            prefilledData = prefilledData,
                            addressCheckerControl = addressCheckerControl,
                            onNextClick = {
                                navigation.handleProceedAction(it, openConfirm)
                            }
                        )
                    }
                }
            }

            BlockchainType.Zcash -> {
                val adapter: ISendZcashAdapter? = getKoinInstance<IAdapterManager>().getAdapterForWallet(wallet)
                if (adapter == null) {
                    MissingWalletAdapterEffect(
                        navigation = navigation,
                        coinUid = wallet.coin.uid,
                        coinCode = wallet.coin.code,
                    )
                } else {
                    val sendZCashViewModel = koinViewModel<SendZCashViewModel> {
                        parametersOf(wallet, address, hideAddress, adapter)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                    ) {
                        SendZCashPageContent(
                            title = title,
                            navigation = navigation,
                            viewModel = sendZCashViewModel,
                            amountInputModeViewModel = amountInputModeViewModel,
                            prefilledData = prefilledData,
                            addressCheckerControl = addressCheckerControl,
                            onNextClick = {
                                navigation.handleProceedAction(it, openConfirm)
                            }
                        )
                    }
                }
            }

            BlockchainType.Ethereum,
            BlockchainType.BinanceSmartChain,
            BlockchainType.Polygon,
            BlockchainType.Avalanche,
            BlockchainType.Optimism,
            BlockchainType.Base,
            BlockchainType.ZkSync,
            BlockchainType.RobinhoodChain,
            BlockchainType.Gnosis,
            BlockchainType.Fantom,
            BlockchainType.ArbitrumOne -> {
                val adapter: ISendEthereumAdapter? = getKoinInstance<IAdapterManager>().getAdapterForWallet(wallet)
                if (adapter == null) {
                    MissingWalletAdapterEffect(
                        navigation = navigation,
                        coinUid = wallet.coin.uid,
                        coinCode = wallet.coin.code,
                    )
                } else {
                    val viewModel = koinViewModel<SendEvmViewModel> {
                        parametersOf(wallet, address, hideAddress, adapter)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                    ) {
                        SendEvmPageContent(
                            title = title,
                            navigation = navigation,
                            viewModel = viewModel,
                            amountInputModeViewModel = amountInputModeViewModel,
                            wallet = wallet,
                            amount = amount,
                            addressCheckerControl = addressCheckerControl,
                            onNextClick = {
                                navigation.handleProceedAction(it, openConfirm)
                            }
                        )
                    }
                }
            }

            BlockchainType.Solana -> {
                val adapter: ISendSolanaAdapter? = getKoinInstance<IAdapterManager>().getAdapterForWallet(wallet)
                if (adapter == null) {
                    MissingWalletAdapterEffect(
                        navigation = navigation,
                        coinUid = wallet.coin.uid,
                        coinCode = wallet.coin.code,
                    )
                } else {
                    val sendSolanaViewModel = koinViewModel<SendSolanaViewModel> {
                        parametersOf(wallet, address, hideAddress, adapter)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                    ) {
                        SendSolanaPageContent(
                            title = title,
                            navigation = navigation,
                            viewModel = sendSolanaViewModel,
                            amountInputModeViewModel = amountInputModeViewModel,
                            prefilledData = prefilledData,
                            addressCheckerControl = addressCheckerControl,
                            onNextClick = {
                                navigation.handleProceedAction(it, openConfirm)
                            }
                        )
                    }
                }
            }

            BlockchainType.Ton -> {
                val adapter: ISendTonAdapter? = getKoinInstance<IAdapterManager>().getAdapterForWallet(wallet)
                if (adapter == null) {
                    MissingWalletAdapterEffect(
                        navigation = navigation,
                        coinUid = wallet.coin.uid,
                        coinCode = wallet.coin.code,
                    )
                } else {
                    val sendTonViewModel = koinViewModel<SendTonViewModel> {
                        parametersOf(wallet, address, hideAddress, adapter)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                    ) {
                        SendTonPageContent(
                            title = title,
                            navigation = navigation,
                            viewModel = sendTonViewModel,
                            amountInputModeViewModel = amountInputModeViewModel,
                            prefilledData = prefilledData,
                            addressCheckerControl = addressCheckerControl,
                            onNextClick = {
                                navigation.handleProceedAction(it, openConfirm)
                            }
                        )
                    }
                }
            }

            BlockchainType.Tron -> {
                val adapter: ISendTronAdapter? = getKoinInstance<IAdapterManager>().getAdapterForWallet(wallet)
                if (adapter == null) {
                    MissingWalletAdapterEffect(
                        navigation = navigation,
                        coinUid = wallet.coin.uid,
                        coinCode = wallet.coin.code,
                    )
                } else {
                    val sendTronViewModel = koinViewModel<SendTronViewModel> {
                        parametersOf(wallet, address, hideAddress, adapter)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                    ) {
                        SendTronPageContent(
                            title = title,
                            navigation = navigation,
                            viewModel = sendTronViewModel,
                            amountInputModeViewModel = amountInputModeViewModel,
                            prefilledData = prefilledData,
                            addressCheckerControl = addressCheckerControl,
                            onNextClick = {
                                navigation.handleProceedAction(it, openConfirm)
                            }
                        )
                    }
                }
            }

            BlockchainType.Monero -> {
                val adapter: ISendMoneroAdapter? = getKoinInstance<IAdapterManager>().getAdapterForWallet(wallet)
                if (adapter == null) {
                    MissingWalletAdapterEffect(
                        navigation = navigation,
                        coinUid = wallet.coin.uid,
                        coinCode = wallet.coin.code,
                    )
                } else {
                    val sendMoneroViewModel = koinViewModel<SendMoneroViewModel> {
                        parametersOf(wallet, address, hideAddress, adapter)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                    ) {
                        SendMoneroPageContent(
                            title = title,
                            navigation = navigation,
                            viewModel = sendMoneroViewModel,
                            amountInputModeViewModel = amountInputModeViewModel,
                            prefilledData = prefilledData,
                            addressCheckerControl = addressCheckerControl,
                            onNextClick = {
                                navigation.handleProceedAction(it, openConfirm)
                            }
                        )
                    }
                }
            }

            BlockchainType.Stellar,
            BlockchainType.Thorchain,
            BlockchainType.Mayachain -> {
                val adapter: ISendMemoAdapter? = getKoinInstance<IAdapterManager>().getAdapterForWallet(wallet)
                if (adapter == null) {
                    MissingWalletAdapterEffect(
                        navigation = navigation,
                        coinUid = wallet.coin.uid,
                        coinCode = wallet.coin.code,
                    )
                } else {
                    val sendMemoViewModel = koinViewModel<SendMemoViewModel> {
                        parametersOf(wallet, address, hideAddress, adapter)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                    ) {
                        SendMemoPageContent(
                            title = title,
                            navigation = navigation,
                            viewModel = sendMemoViewModel,
                            amountInputModeViewModel = amountInputModeViewModel,
                            prefilledData = prefilledData,
                            addressCheckerControl = addressCheckerControl,
                            onNextClick = {
                                navigation.handleProceedAction(it, openConfirm)
                            }
                        )
                    }
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                ) {
                    Text(
                        text = "Unsupported yet",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }

    @Composable
    private fun BeamContent(
        wallet: Wallet,
        title: String,
        navigation: HSNavigation,
        amountInputModeViewModel: AmountInputModeViewModel,
        openConfirm: (Type, Boolean) -> Unit,
    ) {
        val owner: BeamSessionOwner = getKoinInstance()
        val session = owner.current
        val adapter: BeamAdapter? = getKoinInstance<IAdapterManager>().getAdapterForWallet(wallet)
        if (!isNativeBeamSendWallet(wallet, session, adapter) || session == null || adapter == null) {
            MissingWalletAdapterEffect(navigation, wallet.coin.uid, wallet.coin.code)
            return
        }
        // initialize() must run at construction time (inside the Factory), not as a LaunchedEffect:
        // in the very first composition BeamSendScreen -> BeamAddressInput reads recipient/hideAddress
        // to build its own AddressViewModel, so that state must already exist before this composable
        // runs rather than after an effect fires on a later frame.
        val viewModel = koinViewModel<BeamSendViewModel> {
            parametersOf(wallet, session, adapter, input.prefilledData, input.hideAddress)
        }
        BeamSendScreen(
            title = title,
            viewModel = viewModel,
            navigation = navigation,
            inputType = amountInputModeViewModel.inputType,
            onToggleInputType = amountInputModeViewModel::onToggleInputType,
        ) {
            navigation.handleProceedAction(it, openConfirm)
        }
    }

    @Composable
    private fun MissingWalletAdapterEffect(
        navigation: HSNavigation,
        coinUid: String,
        coinCode: String,
    ) {
        val hud = rememberHudMessenger()
        val message = stringResource(Res.string.send_error_adapter_not_found, coinCode)
        LaunchedEffect(coinUid) {
            logger.w { "No adapter for wallet $coinUid" }
            hud.showError(message)
            navigation.navigateUp()
        }
    }

    private fun HSNavigation.handleProceedAction(
        data: ProceedActionData,
        openConfirm: (type: Type, poisonAddress: Boolean) -> Unit,
    ) {
        if (handleBeamProceed(data) { openConfirm(Type.Beam, isAddressSuspicious(data.address)) }) return

        val smartContractCheckEnabledForToken =
            addressCheckerControl.uiState.addressCheckSmartContractEnabled &&
                    isSmartContractCheckSupported(data.wallet.token)

        if (addressCheckerControl.uiState.addressCheckByBaseEnabled ||
            smartContractCheckEnabledForToken
        ) {
            data.address?.let {
                slideFromRight(
                    SecurityCheckPage(
                        SecurityCheckPage.SecurityCheckInput(
                            address = it,
                            wallet = data.wallet,
                            type = data.type,
                            sendEntryPoint = input.sendEntryPoint
                        )
                    )
                )
            }
        } else {
            openConfirm(data.type, isAddressSuspicious(data.address))
        }
    }

    private fun isAddressSuspicious(address: String?): Boolean {
        if (address == null) return false
        return poisonAddressManager.isAddressSuspicious(
            address, input.wallet.token.blockchainType, input.wallet.account.id
        )
    }

    data class Input(
        val wallet: Wallet,
        val title: String,
        val sendEntryPoint: KClass<out HSPage>? = null,
        val prefilledData: PrefilledData? = null,
        val riskyAddress: Boolean = false,
        val hideAddress: Boolean = false
    )

    data class ProceedActionData(
        val address: String?,
        val wallet: Wallet,
        val type: Type
    )
}

/** Warns about a risky or poison address first, then opens the PIN-gated confirmation. */
@Composable
private fun HSNavigation.rememberOpenConfirm(
    riskyAddress: Boolean,
    sendEntryPoint: KClass<out HSPage>?,
    keyboardController: SoftwareKeyboardController?,
): (type: Type, poisonAddress: Boolean) -> Unit {
    val openConfirmation = rememberOpenSendConfirmation(sendEntryPoint)
    val warningLauncher = rememberResultLauncher<AddressRiskySheet.Result, Type> { _, type -> openConfirmation(type) }
    return { type, poisonAddress ->
        val alertText = when {
            riskyAddress -> Res.string.Send_RiskyAddress_AlertText
            poisonAddress -> Res.string.send_poison_address_alert
            else -> null
        }
        if (alertText == null) {
            openConfirmation(type)
        } else {
            keyboardController?.hide()
            warningLauncher.slideFromBottom(
                AddressRiskySheet(AddressRiskySheet.Input(alertText = Translator.getString(alertText))),
                type
            )
        }
    }
}

/** Opens the send confirmation after the transfer PIN, when one is set. */
@Composable
internal fun HSNavigation.rememberOpenSendConfirmation(sendEntryPoint: KClass<out HSPage>?): (Type) -> Unit =
    rememberTransferAction<Type> { type ->
        slideFromRight(SendConfirmationPage(type, sendEntryPoint))
    }

class SendBtcAdvancedSettingsPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val sendBitcoinViewModel =
            navigation.rememberExistingViewModel(SendPage::class, SendBitcoinViewModel::class) ?: return
        val amountInputModeViewModel =
            navigation.rememberExistingViewModel(SendPage::class, AmountInputModeViewModel::class) ?: return
        SendBtcAdvancedSettingsScreen(
            navigation = navigation,
            sendBitcoinViewModel = sendBitcoinViewModel,
            amountInputType = amountInputModeViewModel.inputType,
        )
    }
}

class TransactionInputsSortInfoPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        BtcTransactionInputSortInfoScreen { navigation.navigateUpSafely() }
    }
}

class UtxoExpertModePage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = navigation.rememberExistingViewModel(SendPage::class, SendBitcoinViewModel::class) ?: return
        UtxoExpertModeScreen(
            adapter = viewModel.adapter,
            token = viewModel.wallet.token,
            customUnspentOutputs = viewModel.customUnspentOutputs,
            updateUnspentOutputs = {
                viewModel.updateCustomUnspentOutputs(it)
            },
            onBackClick = {
                navigation.navigateUpSafely()
            }
        )
    }
}
