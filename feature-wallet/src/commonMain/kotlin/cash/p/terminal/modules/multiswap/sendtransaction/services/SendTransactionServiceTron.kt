package cash.p.terminal.modules.multiswap.sendtransaction.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.ISendTronAdapter
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.core.ethereum.toCautionViewItem
import cash.p.terminal.core.isNative
import cash.p.terminal.core.orHide
import cash.p.terminal.core.providers.SharedAppConfig
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.CoinValue
import cash.p.terminal.modules.amount.AmountValidator
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.multiswap.sendtransaction.ISendTransactionService
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionData
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionResult
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionServiceState
import cash.p.terminal.modules.multiswap.sendtransaction.SendTransactionSettings
import cash.p.terminal.modules.send.SendModule
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.tron.SendTronAddressService
import cash.p.terminal.modules.send.tron.DataFieldTronFee
import cash.p.terminal.modules.send.tron.DataFieldTronResources
import cash.p.terminal.modules.send.tron.SendTronFeeService
import cash.p.terminal.modules.send.tron.toTronFeeRows
import cash.p.terminal.modules.send.tron.toTronResourceRows
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.resources.Alert_TitleWarning
import cash.p.terminal.resources.Error_InsufficientBalanceForFee
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.tron_activation_caution
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.horizontalsystems.core.logger.AppLogger
import io.horizontalsystems.tronkit.models.AccountResources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.CurrencyManager

class SendTransactionServiceTron(
    token: Token
) : ISendTransactionService<ISendTronAdapter>(token) {

    val amountValidator = AmountValidator()
    val coinMaxAllowedDecimals = wallet.token.decimals

    val amountService by lazy {
        SendAmountService(
            amountValidator = amountValidator,
            coinCode = wallet.token.coin.code,
            availableBalance = adjustedBalance.setScale(
                coinMaxAllowedDecimals,
                RoundingMode.DOWN
            ),
            leaveSomeBalanceForFee = wallet.token.type.isNative
        )
    }
    val addressService = SendTronAddressService(adapter, wallet.token)
    val xRateService =
        XRateService(getKoinInstance<MarketKitWrapper>(), getKoinInstance<CurrencyManager>().baseCurrency)
    private val feeService = SendTronFeeService(adapter, feeToken)
    private val balanceHiddenManager: IBalanceHiddenManager = getKoinInstance()
    private val balanceHidden: Boolean
        get() = balanceHiddenManager.isWalletBalanceHidden(token.tokenQuery.id)

    val logger: AppLogger = AppLogger("send-tron")

    val blockchainType = wallet.token.blockchainType
    val feeTokenMaxAllowedDecimals = feeToken.decimals
    val fiatMaxAllowedDecimals = SharedAppConfig.fiatDecimal
    private var feeState = feeService.stateFlow.value

    private var networkFee: SendModule.AmountData? = null
    private var sendTransactionData: SendTransactionData.Tron? = null

    private var amountState = amountService.stateFlow.value
    private var addressState = addressService.stateFlow.value

    private val _sendTransactionSettingsFlow = MutableStateFlow(
        SendTransactionSettings.Common
    )
    override val sendTransactionSettingsFlow: StateFlow<SendTransactionSettings> =
        _sendTransactionSettingsFlow.asStateFlow()


    var coinRate by mutableStateOf(xRateService.getRate(token.coin.uid))
        private set
    var feeCoinRate by mutableStateOf(xRateService.getRate(feeToken.coin.uid))
        private set

    private var cautions: List<CautionViewItem> = listOf()
    private var resources: AccountResources? = null
    private var hasEnoughFeeBalance: Boolean = true
    private var feeCaution: CautionViewItem? = null

    private val adjustedBalance: BigDecimal
        get() = adapterManager.getAdjustedBalanceData(wallet)?.available ?: adapter.balanceData.available

    // TRX left after the network fee (activation of the recipient included) is all the swap may send.
    private val availableBalance: BigDecimal
        get() {
            val fee = feeState.breakdown?.totalFee?.takeIf { token.type.isNative } ?: return adjustedBalance
            return (adjustedBalance - fee).max(BigDecimal.ZERO)
        }

    override fun createState() = SendTransactionServiceState(
        availableBalance = availableBalance,
        networkFee = networkFee,
        cautions = cautions + listOfNotNull(activationCaution()),
        feeCaution = feeCaution,
        sendable = hasEnoughFeeBalance && !feeState.estimating && (sendTransactionData != null ||
            (amountState.canBeSend && feeState.canBeSend && addressState.canBeSend)),
        loading = sendTransactionData != null && feeState.estimating,
        fields = listOfNotNull(
            resources?.toTronResourceRows(numberFormatter, balanceHidden)?.takeIf { it.isNotEmpty() }?.let(::DataFieldTronResources)
        ),
        feeDetails = listOfNotNull(
            feeState.breakdown?.toTronFeeRows(numberFormatter, feeToken.coin.code, feeToken.decimals)
                ?.takeIf { it.isNotEmpty() }?.let(::DataFieldTronFee)
        ),
    )

    private fun activationCaution(): CautionViewItem? {
        val breakdown = feeState.breakdown?.takeIf { it.activationFee != null } ?: return null
        val fee = numberFormatter.formatCoinFull(breakdown.totalFee, feeToken.coin.code, feeToken.decimals)
            .orHide(balanceHidden)
        return HSCaution(
            s = TranslatableString.ResString(Res.string.Alert_TitleWarning),
            type = HSCaution.Type.Warning,
            description = TranslatableString.ResString(Res.string.tron_activation_caution, fee),
        ).toCautionViewItem()
    }

    override fun start(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            amountService.stateFlow.collect {
                handleUpdatedAmountState(it)
            }
        }
        coroutineScope.launch {
            addressService.stateFlow.collect {
                handleUpdatedAddressState(it)
            }
        }
        coroutineScope.launch {
            xRateService.getRateFlow(token.coin.uid).collect {
                coinRate = it
            }
        }
        coroutineScope.launch {
            xRateService.getRateFlow(feeToken.coin.uid).collect {
                feeCoinRate = it
            }
        }

        coroutineScope.launch {
            feeService.stateFlow.collect {
                handleUpdatedFeeState(it)
            }
        }

        coroutineScope.launch {
            balanceHiddenManager.anyWalletVisibilityChangedFlow.collect { emitState() }
        }

        coroutineScope.launch {
            adapter.accountResourcesFlow.collect {
                resources = it
                emitState()
            }
        }
    }

    override suspend fun setSendTransactionData(data: SendTransactionData) {
        check(data is SendTransactionData.Tron)

        sendTransactionData = data

        when (data) {
            is SendTransactionData.Tron.WithContract -> {
                feeService.setContract(data.contract)
            }

            is SendTransactionData.Tron.WithCreateTransaction -> {
                feeService.setCreatedTransaction(data.transaction)
            }

            is SendTransactionData.Tron.Regular -> {
                amountService.setAmount(data.amount)
                addressService.setAddress(Address(data.address))
            }
        }
        emitState()
    }

    override suspend fun sendTransaction(mevProtectionEnabled: Boolean): SendTransactionResult {
        val transactionId = try {
            when (val tmpSendTransactionData = sendTransactionData) {
                is SendTransactionData.Tron.WithContract -> {
                    adapter.send(tmpSendTransactionData.contract, feeState.feeLimit)
                }

                is SendTransactionData.Tron.WithCreateTransaction -> {
                    adapter.send(tmpSendTransactionData.transaction)
                }

                is SendTransactionData.Tron.Regular ->
                    adapter.send(
                        amount = amountState.amount!!,
                        to = addressState.tronAddress!!,
                        feeLimit = feeState.feeLimit
                    )

                null -> {
                    adapter.send(
                        amountState.amount!!,
                        addressState.tronAddress!!,
                        feeState.feeLimit
                    )
                }
            }
        } catch (e: Throwable) {
            cautions = listOf(createCaution(e))
            emitState()
            throw e
        }

        markTransactionCreated(transactionId)
        return SendTransactionResult.Tron(SendResult.Sent(transactionId))
    }

    private fun handleUpdatedFeeState(state: SendTronFeeService.State) {
        feeState = state

        networkFee = feeState.fee?.let {
            getAmountData(CoinValue(feeToken, it))
        }

        checkFeeBalance(feeState.fee)
        emitState()
    }

    private suspend fun handleUpdatedAmountState(amountState: SendAmountService.State) {
        this.amountState = amountState
        feeService.setAmount(amountState.amount)
        emitState()
    }

    private suspend fun handleUpdatedAddressState(addressState: SendTronAddressService.State) {
        this.addressState = addressState
        feeService.setTronAddress(addressState.tronAddress)
        emitState()
    }

    private fun checkFeeBalance(fee: BigDecimal?) {
        if (fee == null) {
            hasEnoughFeeBalance = true
            feeCaution = null
            return
        }

        val trxBalance = adapter.trxBalanceData.available
        val requiredTrx = if (token.type.isNative) {
            // If sending TRX, need balance for amount + fee
            (amountState.amount ?: BigDecimal.ZERO) + fee
        } else {
            // If sending TRC20 token, need TRX only for fee
            fee
        }

        hasEnoughFeeBalance = trxBalance >= requiredTrx
        feeCaution = if (!hasEnoughFeeBalance) {
            val formattedBalance = numberFormatter.formatCoinFull(
                trxBalance,
                null,
                feeToken.decimals
            )
            createCaution(
                LocalizedException(Res.string.Error_InsufficientBalanceForFee, feeToken.coin.code, formattedBalance)
            )
        } else {
            null
        }
    }

    override fun hasSettings() = false

    @Composable
    override fun GetSettingsContent(navigation: HSNavigation) = Unit
}
