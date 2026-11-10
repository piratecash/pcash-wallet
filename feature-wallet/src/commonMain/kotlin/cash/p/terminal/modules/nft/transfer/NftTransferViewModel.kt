package cash.p.terminal.modules.nft.transfer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.HardwareWalletSupport
import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.LocalizedException
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.managers.RecentAddressManager
import cash.p.terminal.core.orHide
import cash.p.terminal.entities.Address
import cash.p.terminal.entities.CoinValue
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.modules.memo.fitsMemoBytes
import cash.p.terminal.modules.nft.shortNftAddress
import cash.p.terminal.modules.send.BaseSendViewModel
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.modules.send.fee.insufficientRequiredBalanceText
import cash.p.terminal.modules.send.ton.FeeStatus
import cash.p.terminal.modules.send.ton.NftTransferFeeEstimator
import cash.p.terminal.modules.send.ton.SendTonAddressService
import cash.p.terminal.modules.send.ton.SendTonFeeService
import cash.p.terminal.modules.send.ton.TonFeeQuote
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.resources.Hud_Text_NoInternet
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.not_enough_ton_for_fee
import cash.p.terminal.resources.send_memo_too_long
import cash.p.terminal.resources.swap_insufficient_fee_balance
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.net.UnknownHostException
import kotlin.reflect.KClass

internal val NFT_TRANSFER_ATTACHED = BigDecimal("0.05")

// A TON text comment lives in one cell after a 32-bit opcode.
internal const val NFT_COMMENT_MAX_BYTES = 123

private val logger = Logger.withTag("NftTransferViewModel")

class NftTransferViewModel(
    wallet: Wallet,
    private val item: NftItemRecord,
    private val adapter: ISendTonAdapter,
    private val feeService: SendTonFeeService,
    private val xRateService: XRateService,
    private val contactsRepository: ContactsRepository,
    private val recentAddressManager: RecentAddressManager,
    private val dispatcherProvider: DispatcherProvider,
    adapterManager: IAdapterManager,
) : BaseSendViewModel<NftTransferUiState>(wallet, adapterManager) {

    var sendResult by mutableStateOf<SendResult?>(null)
        private set
    var rate by mutableStateOf(xRateService.getRate(wallet.coin.uid))
        private set

    private val addressService = SendTonAddressService()
    private var comment = ""
    private var addressState = addressService.stateFlow.value
    private var feeState = feeService.stateFlow.value

    private val commentFits: Boolean
        get() = comment.fitsMemoBytes(NFT_COMMENT_MAX_BYTES)

    private val commentToSend: String?
        get() = comment.takeIf { it.isNotBlank() }

    // The fee service keeps the previous success while it debounces new inputs.
    private val currentQuote: TonFeeQuote?
        get() {
            val address = addressState.tonAddress ?: return null
            return feeState.quote?.takeIf { commentFits && it.matches(NFT_TRANSFER_ATTACHED, address, commentToSend) }
        }

    private val requiredTon: BigDecimal?
        get() = currentQuote?.let { (feeState.feeStatus as? FeeStatus.Success)?.requiredTon }

    private val availableBalance: BigDecimal
        get() = displayBalance ?: adapter.balanceData.available

    private val isPoisonAddress: Boolean
        // Entries are stored as text, and one account has several spellings.
        get() {
            val account = addressState.tonAddress?.addrStd
            return listOfNotNull(
                addressState.address?.hex,
                account?.toString(userFriendly = false),
                account?.toString(userFriendly = true, bounceable = true),
                account?.toString(userFriendly = true, bounceable = false),
            ).any { isAddressSuspicious(it) }
        }

    private val isSendingOrSent: Boolean
        get() = sendResult.let { it != null && it !is SendResult.Failed }

    val canContinue: Boolean
        get() {
            val required = requiredTon ?: return false
            return !isSendingOrSent && required <= availableBalance &&
                (!isPoisonAddress || riskAccepted) && isEffectivelySynced
        }

    init {
        addCloseable(feeService)
        feeService.setAmount(NFT_TRANSFER_ATTACHED)
        viewModelScope.launch { feeService.stateFlow.collect { feeState = it; emitState() } }
        viewModelScope.launch { xRateService.getRateFlow(wallet.coin.uid).collect { rate = it; emitState() } }
        viewModelScope.launch { adapter.balanceUpdatedFlow.collect { emitState() } }
        viewModelScope.launch { snapshotFlow { balanceHidden }.collect { emitState() } }
    }

    override fun createState() = NftTransferUiState(
        walletLabel = "${wallet.account.name} · ${item.ownerAddress.shortNftAddress()}",
        recipientDomain = addressState.address?.domain,
        addressError = addressState.addressError,
        isPoisonAddress = isPoisonAddress,
        riskAccepted = riskAccepted,
        comment = comment,
        commentError = commentError(),
        fee = feeText(),
        balance = CoinValue(wallet.token, availableBalance).getFormattedFull().orHide(balanceHidden),
        insufficientBalance = insufficientBalanceText(),
    )

    fun onEnterAddress(address: Address?) {
        addressService.setAddress(address)
        addressState = addressService.stateFlow.value
        resetRiskAccepted()
        syncFeeInputs()
        emitState()
    }

    fun onEnterComment(value: String) {
        comment = value
        syncFeeInputs()
        emitState()
    }

    fun confirmationData(): NftTransferConfirmationData? {
        val recipient = addressState.address ?: return null
        val quote = currentQuote ?: return null
        val contact = contactsRepository
            .getContactsFiltered(BlockchainType.Ton, addressQuery = recipient.hex)
            .firstOrNull()
        return NftTransferConfirmationData(recipient, quote.memo, quote.fee, contact)
    }

    public override fun getEstimatedFee(): BigDecimal? = currentQuote?.fee

    override fun onSendRequested() {
        if (!canContinue) return
        val recipient = addressState.address ?: return
        val quote = currentQuote ?: return
        sendResult = SendResult.Sending
        viewModelScope.launch { transfer(recipient, quote) }
    }

    private suspend fun transfer(recipient: Address, quote: TonFeeQuote) = withContext(dispatcherProvider.io) {
        try {
            adapter.sendNftTransfer(item.address, quote.address, quote.memo)
        } catch (e: CancellationException) {
            // The broadcast outcome is unknown, so this must not become a retryable failure.
            throw e
        } catch (e: Throwable) {
            sendResult = if (HardwareWalletSupport.isUserCancellation(e)) null else SendResult.Failed(createCaution(e))
            return@withContext
        }
        sendResult = SendResult.Sent()
        rememberRecipient(recipient)
    }

    private fun rememberRecipient(recipient: Address) {
        try {
            onSendSuccess(recipient.hex)
            recentAddressManager.setRecentAddress(recipient, BlockchainType.Ton)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Could not record the recipient after a successful broadcast" }
        }
    }

    // Withholding the address stops the estimate, so an oversized comment never reaches the estimator.
    private fun syncFeeInputs() {
        if (commentFits) feeService.setMemo(commentToSend)
        feeService.setTonAddress(addressState.tonAddress.takeIf { commentFits })
    }

    private fun commentError(): TranslatableString? = if (commentFits) {
        null
    } else {
        TranslatableString.ResString(
            Res.string.send_memo_too_long,
            comment.encodeToByteArray().size,
            NFT_COMMENT_MAX_BYTES,
        )
    }

    private fun feeText(): String {
        val fee = currentQuote?.fee ?: return formatFeePrimary(null)
        val secondary = formatFeeSecondary(fee, rate)
        return listOf("≈ ${formatFeePrimary(fee)}", secondary)
            .filter(String::isNotEmpty)
            .joinToString(" · ")
            .orHide(balanceHidden)
    }

    private fun insufficientBalanceText(): TranslatableString? {
        val exact = exactInsufficientBalanceText() ?: return null
        return if (balanceHidden) {
            TranslatableString.ResString(Res.string.swap_insufficient_fee_balance, wallet.coin.code)
        } else {
            exact
        }
    }

    private fun exactInsufficientBalanceText(): TranslatableString? {
        if (feeState.feeStatus is FeeStatus.NoEnoughBalance) {
            return TranslatableString.ResString(Res.string.not_enough_ton_for_fee, availableBalance.toPlainString())
        }
        val required = requiredTon?.takeIf { it > availableBalance } ?: return null
        return insufficientRequiredBalanceText(required, availableBalance, wallet.token)
    }

    private fun createCaution(error: Throwable) = when (error) {
        is UnknownHostException -> HSCaution(TranslatableString.ResString(Res.string.Hud_Text_NoInternet))
        is LocalizedException -> HSCaution(TranslatableString.ResString(error.errorTextRes))
        else -> HSCaution(TranslatableString.PlainString(error.message ?: ""))
    }
}

data class NftTransferUiState(
    val walletLabel: String,
    val recipientDomain: String?,
    val addressError: Throwable?,
    val isPoisonAddress: Boolean,
    val riskAccepted: Boolean,
    val comment: String,
    val commentError: TranslatableString?,
    val fee: String,
    val balance: String,
    val insufficientBalance: TranslatableString?,
)

data class NftTransferConfirmationData(
    val address: Address,
    val comment: String?,
    val fee: BigDecimal,
    val contact: Contact?,
)

class NftTransferViewModelFactory(
    private val item: NftItemRecord,
    private val wallet: Wallet,
    private val adapter: ISendTonAdapter,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T =
        NftTransferViewModel(
            wallet = wallet,
            item = item,
            adapter = adapter,
            feeService = SendTonFeeService(NftTransferFeeEstimator(adapter, item.address)),
            xRateService = XRateService(getKoinInstance(), getKoinInstance<CurrencyManager>().baseCurrency),
            contactsRepository = getKoinInstance(),
            recentAddressManager = getKoinInstance(),
            dispatcherProvider = getKoinInstance(),
            adapterManager = getKoinInstance(),
        ) as T
}
