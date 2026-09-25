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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.ISendBitcoinAdapter
import cash.p.terminal.core.ISendEthereumAdapter
import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.core.ISendSolanaAdapter
import cash.p.terminal.core.ISendMoneroAdapter
import cash.p.terminal.core.ISendStellarAdapter
import cash.p.terminal.core.ISendTronAdapter
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.amount.AmountInputModeModule
import cash.p.terminal.modules.amount.AmountInputModeViewModel
import cash.p.terminal.modules.pin.ConfirmPinPage
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.send.SendConfirmationPage.Type
import cash.p.terminal.modules.send.address.AddressCheckerControl
import cash.p.terminal.modules.send.address.isSmartContractCheckSupported
import cash.p.terminal.modules.send.bitcoin.SendBitcoinModule
import cash.p.terminal.modules.send.bitcoin.SendBitcoinScreen
import cash.p.terminal.modules.send.bitcoin.SendBitcoinViewModel
import cash.p.terminal.modules.send.bitcoin.advanced.BtcTransactionInputSortInfoScreen
import cash.p.terminal.modules.send.bitcoin.advanced.SendBtcAdvancedSettingsScreen
import cash.p.terminal.modules.send.bitcoin.utxoexpert.UtxoExpertModeScreen
import cash.p.terminal.modules.send.evm.SendEvmModule
import cash.p.terminal.modules.send.evm.SendEvmPageContent
import cash.p.terminal.modules.send.evm.SendEvmViewModel
import cash.p.terminal.modules.send.monero.SendMoneroModule
import cash.p.terminal.modules.send.monero.SendMoneroPageContent
import cash.p.terminal.modules.send.monero.SendMoneroViewModel
import cash.p.terminal.modules.send.securitycheck.SecurityCheckPage
import cash.p.terminal.modules.send.solana.SendSolanaModule
import cash.p.terminal.modules.send.solana.SendSolanaPageContent
import cash.p.terminal.modules.send.solana.SendSolanaViewModel
import cash.p.terminal.modules.send.stellar.SendStellarModule
import cash.p.terminal.modules.send.stellar.SendStellarPageContent
import cash.p.terminal.modules.send.stellar.SendStellarViewModel
import cash.p.terminal.modules.send.ton.SendTonModule
import cash.p.terminal.modules.send.ton.SendTonPageContent
import cash.p.terminal.modules.send.ton.SendTonViewModel
import cash.p.terminal.modules.send.tron.SendTronModule
import cash.p.terminal.modules.send.tron.SendTronPageContent
import cash.p.terminal.modules.send.tron.SendTronViewModel
import cash.p.terminal.modules.send.zcash.SendZCashModule
import cash.p.terminal.modules.send.zcash.SendZCashPageContent
import cash.p.terminal.modules.send.zcash.SendZCashViewModel
import cash.p.terminal.modules.sendtokenselect.PrefilledData
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.managers.PoisonAddressManager
import org.koin.java.KoinJavaComponent.inject
import timber.log.Timber
import kotlin.reflect.KClass

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

        val amountInputModeViewModel = viewModel<AmountInputModeViewModel>(
            factory = AmountInputModeModule.Factory(wallet.coin.uid)
        )

        when (wallet.token.blockchainType) {
            BlockchainType.Bitcoin,
            BlockchainType.BitcoinCash,
            BlockchainType.ECash,
            BlockchainType.Litecoin,
            BlockchainType.Dogecoin,
            BlockchainType.PirateCash,
            BlockchainType.Cosanta,
            BlockchainType.Dash -> {
                val adapter: ISendBitcoinAdapter? = App.adapterManager.getAdapterForWallet(wallet)
                if (adapter == null) {
                    MissingWalletAdapterEffect(
                        navigation = navigation,
                        coinUid = wallet.coin.uid,
                        coinCode = wallet.coin.code,
                    )
                } else {
                    val factory = SendBitcoinModule.Factory(wallet, address, hideAddress, adapter)
                    val sendBitcoinViewModel = viewModel<SendBitcoinViewModel>(factory = factory)
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
                                navigation.handleProceedAction(it, keyboardController)
                            }
                        )
                    }
                }
            }

            BlockchainType.Zcash -> {
                val adapter: ISendZcashAdapter? = App.adapterManager.getAdapterForWallet(wallet)
                if (adapter == null) {
                    MissingWalletAdapterEffect(
                        navigation = navigation,
                        coinUid = wallet.coin.uid,
                        coinCode = wallet.coin.code,
                    )
                } else {
                    val factory = SendZCashModule.Factory(wallet, address, hideAddress, adapter)
                    val sendZCashViewModel = viewModel<SendZCashViewModel>(factory = factory)
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
                                navigation.handleProceedAction(it, keyboardController)
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
                val adapter: ISendEthereumAdapter? = App.adapterManager.getAdapterForWallet(wallet)
                if (adapter == null) {
                    MissingWalletAdapterEffect(
                        navigation = navigation,
                        coinUid = wallet.coin.uid,
                        coinCode = wallet.coin.code,
                    )
                } else {
                    val factory = SendEvmModule.Factory(wallet, address, hideAddress, adapter)
                    val viewModel = viewModel<SendEvmViewModel>(factory = factory)
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
                                navigation.handleProceedAction(it, keyboardController)
                            }
                        )
                    }
                }
            }

            BlockchainType.Solana -> {
                val adapter: ISendSolanaAdapter? = App.adapterManager.getAdapterForWallet(wallet)
                if (adapter == null) {
                    MissingWalletAdapterEffect(
                        navigation = navigation,
                        coinUid = wallet.coin.uid,
                        coinCode = wallet.coin.code,
                    )
                } else {
                    val factory = SendSolanaModule.Factory(wallet, address, hideAddress, adapter)
                    val sendSolanaViewModel = viewModel<SendSolanaViewModel>(factory = factory)
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
                                navigation.handleProceedAction(it, keyboardController)
                            }
                        )
                    }
                }
            }

            BlockchainType.Ton -> {
                val adapter: ISendTonAdapter? = App.adapterManager.getAdapterForWallet(wallet)
                if (adapter == null) {
                    MissingWalletAdapterEffect(
                        navigation = navigation,
                        coinUid = wallet.coin.uid,
                        coinCode = wallet.coin.code,
                    )
                } else {
                    val factory = SendTonModule.Factory(wallet, address, hideAddress, adapter)
                    val sendTonViewModel = viewModel<SendTonViewModel>(factory = factory)
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
                                navigation.handleProceedAction(it, keyboardController)
                            }
                        )
                    }
                }
            }

            BlockchainType.Tron -> {
                val adapter: ISendTronAdapter? = App.adapterManager.getAdapterForWallet(wallet)
                if (adapter == null) {
                    MissingWalletAdapterEffect(
                        navigation = navigation,
                        coinUid = wallet.coin.uid,
                        coinCode = wallet.coin.code,
                    )
                } else {
                    val factory = SendTronModule.Factory(wallet, address, hideAddress, adapter)
                    val sendTronViewModel = viewModel<SendTronViewModel>(factory = factory)
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
                                navigation.handleProceedAction(it, keyboardController)
                            }
                        )
                    }
                }
            }

            BlockchainType.Monero -> {
                val adapter: ISendMoneroAdapter? = App.adapterManager.getAdapterForWallet(wallet)
                if (adapter == null) {
                    MissingWalletAdapterEffect(
                        navigation = navigation,
                        coinUid = wallet.coin.uid,
                        coinCode = wallet.coin.code,
                    )
                } else {
                    val factory = SendMoneroModule.Factory(wallet, address, hideAddress, adapter)
                    val sendMoneroViewModel = viewModel<SendMoneroViewModel>(factory = factory)
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
                                navigation.handleProceedAction(it, keyboardController)
                            }
                        )
                    }
                }
            }

            BlockchainType.Stellar -> {
                val adapter: ISendStellarAdapter? = App.adapterManager.getAdapterForWallet(wallet)
                if (adapter == null) {
                    MissingWalletAdapterEffect(
                        navigation = navigation,
                        coinUid = wallet.coin.uid,
                        coinCode = wallet.coin.code,
                    )
                } else {
                    val factory = SendStellarModule.Factory(wallet, address, hideAddress, adapter)
                    val sendStellarViewModel = viewModel<SendStellarViewModel>(factory = factory)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                    ) {
                        SendStellarPageContent(
                            title = title,
                            navigation = navigation,
                            viewModel = sendStellarViewModel,
                            amountInputModeViewModel = amountInputModeViewModel,
                            prefilledData = prefilledData,
                            addressCheckerControl = addressCheckerControl,
                            onNextClick = {
                                navigation.handleProceedAction(it, keyboardController)
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
    private fun MissingWalletAdapterEffect(
        navigation: HSNavigation,
        coinUid: String,
        coinCode: String,
    ) {
        val view = LocalView.current
        val message = stringResource(R.string.send_error_adapter_not_found, coinCode)
        LaunchedEffect(coinUid) {
            Timber.w("No adapter for wallet %s", coinUid)
            HudHelper.showErrorMessage(view, message)
            navigation.navigateUp()
        }
    }

    private fun HSNavigation.handleProceedAction(
        data: ProceedActionData,
        keyboardController: SoftwareKeyboardController?
    ) {
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
            openConfirm(
                type = data.type,
                riskyAddress = input.riskyAddress,
                poisonAddress = isAddressSuspicious(data.address),
                keyboardController = keyboardController,
                sendEntryPoint = input.sendEntryPoint
            )
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

internal fun HSNavigation.openConfirm(
    type: Type,
    riskyAddress: Boolean,
    keyboardController: SoftwareKeyboardController?,
    sendEntryPoint: KClass<out HSPage>?,
    poisonAddress: Boolean = false,
) {
    if (riskyAddress) {
        keyboardController?.hide()
        slideFromBottomForResult<AddressRiskySheet.Result>(
            AddressRiskySheet(
                AddressRiskySheet.Input(
                    alertText = Translator.getString(R.string.Send_RiskyAddress_AlertText)
                )
            )
        ) {
            openConfirm(type, sendEntryPoint)
        }
    } else if (poisonAddress) {
        keyboardController?.hide()
        slideFromBottomForResult<AddressRiskySheet.Result>(
            AddressRiskySheet(
                AddressRiskySheet.Input(
                    alertText = Translator.getString(R.string.send_poison_address_alert)
                )
            )
        ) {
            openConfirm(type, sendEntryPoint)
        }
    } else {
        openConfirm(type, sendEntryPoint)
    }
}

private fun HSNavigation.openConfirm(
    type: Type,
    sendEntryPoint: KClass<out HSPage>?
) {
    authorizedAction(
        ConfirmPinPage.InputConfirm(
            descriptionResId = R.string.Unlock_EnterPasscode,
            pinType = PinType.TRANSFER
        )
    ) {
        slideFromRight(SendConfirmationPage(type, sendEntryPoint))
    }
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
