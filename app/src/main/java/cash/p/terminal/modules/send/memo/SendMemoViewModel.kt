package cash.p.terminal.modules.send.memo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import cash.p.terminal.R
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.INativeBalanceProvider
import cash.p.terminal.core.ISendMemoAdapter
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.OfflineTransactionAdapter
import cash.p.terminal.core.SignedOfflineMemoTransaction
import cash.p.terminal.core.getFeeTokenBalance
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.core.managers.RecentAddressManager
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.core.toResString
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.OfflineSignedTransactionDraft
import cash.p.terminal.entities.PendingTransactionDraft
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.send.BaseSendViewModel
import cash.p.terminal.modules.send.SendConfirmationData
import cash.p.terminal.modules.send.SendErrorInsufficientBalance
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.hasInsufficientFeeTokenBalance
import cash.p.terminal.modules.send.offline.OfflineSignCapableViewModel
import cash.p.terminal.modules.send.offline.OfflineSigningController
import cash.p.terminal.modules.send.offline.OfflineTransactionFormat
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.tangem.domain.isHardwareWalletUserCancelled
import cash.p.terminal.modules.send.isHardwareWalletCancelled
import cash.p.terminal.trezor.domain.TrezorCancelledException
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.getMaxSendableBalance
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.logger.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.net.UnknownHostException

@Suppress("LongParameterList")
class SendMemoViewModel(
    wallet: Wallet,
    private val sendToken: Token,
    override val feeToken: Token,
    private val adapter: ISendMemoAdapter,
    override val coinMaxAllowedDecimals: Int,
    private val xRateService: XRateService,
    address: Address?,
    private val showAddressInput: Boolean,
    private val amountService: SendAmountService,
    private val addressService: SendMemoAddressService,
    private val contactsRepo: ContactsRepository,
    private val minimumAmountService: SendMemoMinimumAmountService,
    private val adapterManager: IAdapterManager,
    private val dispatcherProvider: DispatcherProvider,
    private val recentAddressManager: RecentAddressManager,
    private val offlineTransactionPayloadEncoder: OfflineTransactionPayloadEncoder,
    private val offlineSignedTransactionRepository: OfflineSignedTransactionRepository,
    private val chain: SendMemoChain,
    private val pendingRegistrar: PendingTransactionRegistrar,
) : BaseSendViewModel<SendMemoUiState>(wallet, adapterManager), OfflineSignCapableViewModel {
    data class OfflineSignResult(
        val signedTransaction: SignedOfflineMemoTransaction,
        val confirmationData: SendConfirmationData,
    )

    private val fee: BigDecimal
        get() = adapter.sendFee

    val blockchainType = wallet.token.blockchainType
    val feeTokenMaxAllowedDecimals = feeToken.decimals
    override val feeCoinMaxAllowedDecimals get() = feeTokenMaxAllowedDecimals
    val fiatMaxAllowedDecimals = AppConfigProvider.fiatDecimal
    val memoMaxLength = chain.memoMaxLength
    val memoMaxBytes = chain.memoMaxBytes

    @Suppress("UNCHECKED_CAST")
    private val offlineSignAdapter = adapter as? OfflineTransactionAdapter<SignedOfflineMemoTransaction>
    override val offlineSigningController: OfflineSigningController<OfflineSignResult> by lazy {
        OfflineSigningController(
            scope = viewModelScope,
            dispatcherProvider = dispatcherProvider,
            payloadEncoder = offlineTransactionPayloadEncoder,
            repository = offlineSignedTransactionRepository,
            cautionFactory = ::createCaution,
            isSilentCancellation = { it.isHardwareWalletCancelled() },
        )
    }

    private var amountState = amountService.stateFlow.value
    private var addressState = addressService.stateFlow.value
    private var minimumAmountState = minimumAmountService.stateFlow.value
    private var memo: String? = null

    override var coinRate by mutableStateOf(xRateService.getRate(sendToken.coin.uid))
        private set
    var feeCoinRate by mutableStateOf(xRateService.getRate(feeToken.coin.uid))
        private set
    override var sendResult by mutableStateOf<SendResult?>(null)
        private set

    override val offlineSignSupported = offlineSignAdapter != null && !wallet.account.isWatchAccount

    private val decimalAmount: BigDecimal
        get() = amountState.amount ?: throw LocalizedException(R.string.send_error_amount_unavailable)

    private val destinationAddress: Address
        get() = addressState.address ?: throw LocalizedException(R.string.send_error_address_unavailable)

    private val logger: AppLogger = AppLogger("send-${blockchainType.uid}")

    override fun getEstimatedFee(): BigDecimal = fee
    override fun onSendRequested() = onClickSend()

    init {
        viewModelScope.launch(dispatcherProvider.default) {
            amountService.stateFlow.collect {
                handleUpdatedAmountState(it)
            }
        }
        viewModelScope.launch(dispatcherProvider.default) {
            addressService.stateFlow.collect {
                handleUpdatedAddressState(it)
            }
        }
        viewModelScope.launch(dispatcherProvider.default) {
            minimumAmountService.stateFlow.collect {
                handleUpdatedMinimumAmountState(it)
            }
        }
        viewModelScope.launch(dispatcherProvider.default) {
            merge(adapter.sendFeeUpdatedFlow, adapter.balanceUpdatedFlow).collect {
                val maxSendable = adapterManager.getMaxSendableBalance(wallet, adapter.maxSpendableBalance)
                amountService.updateAvailableBalance(maxSendable)
                emitState()
            }
        }
        (adapter as? INativeBalanceProvider)?.let { feeBalanceProvider ->
            viewModelScope.launch(dispatcherProvider.default) {
                feeBalanceProvider.nativeBalanceUpdatedFlow.collect { emitState() }
            }
        }
        viewModelScope.launch(dispatcherProvider.default) {
            xRateService.getRateFlow(sendToken.coin.uid).collect {
                coinRate = it
            }
        }
        viewModelScope.launch(dispatcherProvider.default) {
            xRateService.getRateFlow(feeToken.coin.uid).collect {
                feeCoinRate = it
            }
        }

        addressService.setAddress(address)
    }

    override fun createState(): SendMemoUiState {
        val poison = isAddressSuspicious(addressState.address?.hex)
        val feeCaution = insufficientFeeCaution()
        return SendMemoUiState(
            availableBalance = amountState.availableBalance,
            amountCaution = amountState.amountCaution ?: feeCaution,
            addressError = addressState.addressError,
            minimumAmountError = minimumAmountState.error,
            canBeSend = amountState.canBeSend && addressState.canBeSend && minimumAmountState.canBeSend &&
                feeCaution == null && memoFits() && (!poison || riskAccepted),
            showAddressInput = showAddressInput,
            fee = fee,
            address = addressState.address,
            isPoisonAddress = poison,
            riskAccepted = riskAccepted,
        )
    }

    fun onEnterAmount(amount: BigDecimal?) {
        amountService.setAmount(amount)
    }

    fun onEnterAddress(address: Address?) {
        resetRiskAccepted()
        addressService.setAddress(address)
    }

    fun onEnterMemo(memo: String) {
        this.memo = memo.ifBlank { null }
        emitState()
    }

    // Read from the adapter whose native-balance event triggers the re-check; the fee-coin wallet's
    // cache updates on its own coroutine and may still hold the old value.
    private fun insufficientFeeCaution(): HSCaution? {
        val feeBalance = (adapter as? INativeBalanceProvider)?.nativeBalanceData?.available
            ?: adapterManager.getFeeTokenBalance(feeToken, sendToken)
        if (!hasInsufficientFeeTokenBalance(sendToken, fee, feeBalance)) return null
        return SendErrorInsufficientBalance(feeToken.coin.code, (feeBalance ?: BigDecimal.ZERO).toPlainString())
    }

    private fun memoFits(): Boolean {
        val maxBytes = chain.memoMaxBytes ?: return true
        return (memo?.encodeToByteArray()?.size ?: 0) <= maxBytes
    }

    override fun getConfirmationData(): SendConfirmationData {
        val address = destinationAddress
        val contact = contactsRepo.getContactsFiltered(
            blockchainType,
            addressQuery = address.hex
        ).firstOrNull()
        return SendConfirmationData(
            amount = decimalAmount,
            fee = fee,
            address = address,
            contact = contact,
            coin = wallet.coin,
            feeCoin = feeToken.coin,
            memo = memo,
        )
    }

    fun onClickSend() {
        logger.info("click send button")
        sendResult = SendResult.Sending

        viewModelScope.launch {
            send()
        }
    }

    override fun onClickSignOffline(format: OfflineTransactionFormat) {
        offlineSigningController.sign(
            format = format,
            producer = ::signedOfflineTransaction,
            draftBuilder = ::offlineSignedTransactionDraft,
        )
    }

    private suspend fun send() = withContext(dispatcherProvider.io) {
        var pendingTxId: String? = null
        val (address, txHash) = try {
            logger.info("sending tx")
            val address = destinationAddress
            val amount = decimalAmount
            if (chain.registersPendingDraft) {
                pendingTxId = pendingRegistrar.register(pendingTransactionDraft(amount, address))
            }
            address to adapter.send(amount, address.hex, memo)
        } catch (error: CancellationException) {
            // The broadcast may have happened: the draft stays for the pending matcher.
            throw error
        } catch (error: Throwable) {
            pendingTxId?.let { pendingRegistrar.deleteFailed(it) }
            onSendFailed(error)
            return@withContext
        }
        recordSent(pendingTxId, address, txHash)
        sendResult = SendResult.Sent(txHash)
        logger.info("success")
    }

    private fun onSendFailed(error: Throwable) {
        if (error is TrezorCancelledException || error.isHardwareWalletUserCancelled()) {
            sendResult = null
            logger.info("user cancelled")
            return
        }
        sendResult = SendResult.Failed(createCaution(error))
        logger.warning("failed", error)
    }

    // The node already has the transaction: a local bookkeeping failure must not report it as failed.
    private suspend fun recordSent(pendingTxId: String?, address: Address, txHash: String?) {
        locallyCreatedTransactionRepository.markCreated(wallet, txHash)
        try {
            if (pendingTxId != null && txHash != null) pendingRegistrar.updateTxId(pendingTxId, txHash)
            onSendSuccess(address.hex)
            recentAddressManager.setRecentAddress(address, blockchainType)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.warning("post-send bookkeeping failed", error)
        }
    }

    private fun pendingTransactionDraft(amount: BigDecimal, address: Address) = PendingTransactionDraft(
        wallet = wallet,
        token = sendToken,
        amount = amount,
        fee = fee,
        sdkBalanceAtCreation = adapter.balanceData.available,
        fromAddress = "",
        toAddress = address.hex,
        memo = memo,
    )

    private suspend fun signedOfflineTransaction(): OfflineSignResult {
        val confirmationData = getConfirmationData()
        val signingAdapter = offlineSignAdapter
            ?: throw LocalizedException(R.string.offline_broadcast_unsupported_blockchain)
        return OfflineSignResult(
            signedTransaction = signingAdapter.signOffline(
                chain.offlineSignRequest(
                    amount = confirmationData.amount,
                    address = confirmationData.address.hex,
                    memo = confirmationData.memo,
                )
            ),
            confirmationData = confirmationData,
        )
    }

    private fun offlineSignedTransactionDraft(result: OfflineSignResult): OfflineSignedTransactionDraft {
        val confirmationData = result.confirmationData
        val signed = result.signedTransaction
        return OfflineSignedTransactionDraft(
            wallet = wallet,
            amount = confirmationData.amount,
            fee = signed.fee,
            toAddress = confirmationData.address.hex,
            rawHex = signed.rawHex,
            txHash = signed.txHash,
            inputOutpoints = emptyList(),
            feeToken = feeToken,
            stellarRetryMetadata = chain.offlineRetryMetadata(signed),
        )
    }

    private fun createCaution(error: Throwable) = when (error) {
        is UnknownHostException -> HSCaution(TranslatableString.ResString(R.string.Hud_Text_NoInternet))
        is LocalizedException -> HSCaution(error.toResString())
        else -> HSCaution(TranslatableString.PlainString(error.message ?: ""))
    }

    private fun handleUpdatedMinimumAmountState(state: SendMemoMinimumAmountService.State) {
        minimumAmountState = state

        amountService.setMinimumSendAmount(minimumAmountState.minimumAmount)

        emitState()
    }

    private fun handleUpdatedAmountState(amountState: SendAmountService.State) {
        this.amountState = amountState

        emitState()
    }

    private suspend fun handleUpdatedAddressState(addressState: SendMemoAddressService.State) {
        this.addressState = addressState

        minimumAmountService.setValidAddress(addressState.validAddress)

        emitState()
    }
}

data class SendMemoUiState(
    val availableBalance: BigDecimal?,
    val amountCaution: HSCaution?,
    val addressError: Throwable?,
    val minimumAmountError: Throwable?,
    val canBeSend: Boolean,
    val showAddressInput: Boolean,
    val fee: BigDecimal?,
    val address: Address?,
    val isPoisonAddress: Boolean = false,
    val riskAccepted: Boolean = false,
)
