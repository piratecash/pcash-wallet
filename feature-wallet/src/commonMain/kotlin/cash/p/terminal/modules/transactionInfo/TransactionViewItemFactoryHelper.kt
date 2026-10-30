package cash.p.terminal.modules.transactionInfo

import cash.p.beam.BeamTransactionDirection
import cash.p.terminal.entities.transactionrecords.beam.BeamTransactionRecord
import cash.p.terminal.modules.transactions.failureReasonText
import cash.p.terminal.modules.transactions.statusTitle
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.isCustom
import cash.p.terminal.core.managers.AddressMetadataManager
import cash.p.terminal.core.orHide
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.entities.LastBlockInfo
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.TransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.entities.transactionrecords.bitcoin.TransactionLockState
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.monero.MoneroTransactionRecord
import cash.p.terminal.entities.transactionrecords.solana.SolanaTransactionRecord
import cash.p.terminal.entities.transactionrecords.stellar.StellarTransactionRecord
import cash.p.terminal.entities.transactionrecords.thorchain.ThorchainTransactionRecord
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.entities.transactionrecords.tron.TronTransactionRecord
import cash.p.terminal.modules.transactions.AmlStatus
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.modules.transactions.TransactionViewItem
import cash.p.terminal.modules.transactions.getSenderAddresses
import cash.p.terminal.modules.transactions.isIncomingForAmlCheck
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.icon_24_nft_placeholder
import io.horizontalsystems.core.Translator
import cash.p.terminal.ui_compose.ColorName
import cash.p.terminal.ui_compose.ColoredValue
import cash.p.terminal.ui_compose.amountColor
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.CurrencyValue
import io.horizontalsystems.core.helpers.DateHelper
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Date
import kotlin.math.min
import cash.p.terminal.resources.Balance_RatePerCoin
import cash.p.terminal.resources.NotAvailable
import cash.p.terminal.resources.Swap_AmountMax
import cash.p.terminal.resources.Swap_AmountMin
import cash.p.terminal.resources.Swap_SwapProvider_Title
import cash.p.terminal.resources.TransactionInfo_AnnouncedAmount
import cash.p.terminal.resources.TransactionInfo_ButtonViewOnExplorerName
import cash.p.terminal.resources.TransactionInfo_Change_Address
import cash.p.terminal.resources.TransactionInfo_Date
import cash.p.terminal.resources.TransactionInfo_ExecutedRate
import cash.p.terminal.resources.TransactionInfo_Fee
import cash.p.terminal.resources.TransactionInfo_FeeEstimated
import cash.p.terminal.resources.TransactionInfo_From
import cash.p.terminal.resources.TransactionInfo_HistoricalRate
import cash.p.terminal.resources.TransactionInfo_LockedUntil
import cash.p.terminal.resources.TransactionInfo_Memo
import cash.p.terminal.resources.TransactionInfo_Price
import cash.p.terminal.resources.TransactionInfo_PriceImpact
import cash.p.terminal.resources.TransactionInfo_RecipientHash
import cash.p.terminal.resources.TransactionInfo_Spender
import cash.p.terminal.resources.TransactionInfo_Status
import cash.p.terminal.resources.TransactionInfo_To
import cash.p.terminal.resources.TransactionInfo_UnlockedAt
import cash.p.terminal.resources.Transaction_Unlimited
import cash.p.terminal.resources.Transactions_Burn
import cash.p.terminal.resources.Transactions_ContractCall
import cash.p.terminal.resources.Transactions_ContractCreation
import cash.p.terminal.resources.Transactions_Mint
import cash.p.terminal.resources.Transactions_Receive
import cash.p.terminal.resources.Transactions_Send
import cash.p.terminal.resources.beam_history_failure_reason
import cash.p.terminal.resources.beam_history_kernel_id
import cash.p.terminal.resources.ic_lock_20
import cash.p.terminal.resources.ic_unlock_20
import cash.p.terminal.resources.transaction_info_executed_amount
import cash.p.terminal.resources.transaction_info_sync_source

object TransactionViewItemFactoryHelper {
    val zeroAddress = "0x0000000000000000000000000000000000000000"
    private val numberFormatter: IAppNumberFormatter
        get() = getKoinInstance()
    private val addressMetadataManager: AddressMetadataManager
        get() = getKoinInstance()

    fun getMemoItem(memo: String) =
        TransactionInfoViewItem.Value(
            Translator.getString(Res.string.TransactionInfo_Memo),
            memo
        )

    private fun getFeeAmountString(
        rate: CurrencyValue?,
        transactionValue: TransactionValue,
    ): String {
        val feeInFiat = rate?.let {
            transactionValue.decimalValue?.let { decimalValue ->
                numberFormatter.formatFiatFull(
                    it.value * decimalValue,
                    it.currency.symbol
                )
            }
        }
        val feeInCoin = transactionValue.decimalValue?.let { decimalValue ->
            numberFormatter.formatCoinFull(decimalValue, transactionValue.coinCode, 8)
        } ?: ""

        return feeInCoin + (if (feeInFiat != null) " | $feeInFiat" else "")
    }

    private fun getDoubleSpendViewItem(transactionHash: String, conflictingHash: String) =
        TransactionInfoViewItem.DoubleSpend(transactionHash, conflictingHash)

    private fun getLockStateItem(lockState: TransactionLockState?): TransactionInfoViewItem? {
        return lockState?.let {
            val leftIcon = if (it.locked) Res.drawable.ic_lock_20 else Res.drawable.ic_unlock_20
            val date = DateHelper.getFullDate(it.date)
            val title = Translator.getString(
                if (it.locked) Res.string.TransactionInfo_LockedUntil else Res.string.TransactionInfo_UnlockedAt,
                date
            )
            TransactionInfoViewItem.LockState(title, leftIcon, it.date, it.locked)
        }
    }

    private fun getFee(
        transactionValue: TransactionValue,
        rate: CurrencyValue?,
    ): TransactionInfoViewItem {
        val feeAmountString = getFeeAmountString(rate, transactionValue)

        return TransactionInfoViewItem.Value(
            Translator.getString(Res.string.TransactionInfo_Fee),
            feeAmountString
        )
    }

    private fun getFeeItem(
        transactionValue: TransactionValue,
        rate: CurrencyValue?,
        status: TransactionStatus,
    ): TransactionInfoViewItem.Value {
        val feeAmountString = getFeeAmountString(rate, transactionValue)
        val feeTitle: String = when (status) {
            TransactionStatus.Pending -> Translator.getString(Res.string.TransactionInfo_FeeEstimated)
            is TransactionStatus.Processing,
            TransactionStatus.Failed,
            TransactionStatus.Completed,
                -> Translator.getString(Res.string.TransactionInfo_Fee)
        }

        return TransactionInfoViewItem.Value(feeTitle, feeAmountString)
    }

    private fun getAddressItems(
        title: String,
        address: String,
        blockchainType: BlockchainType,
        showCopyWarning: Boolean = false,
    ): List<TransactionInfoViewItem> {
        val (contact, label) = addressMetadataManager.get(blockchainType, address)
        val addressTitle = label?.let { "$title · $it" } ?: title

        return buildList {
            add(
                TransactionInfoViewItem.Address(
                    addressTitle,
                    address,
                    contact == null && label == null,
                    blockchainType,
                    showCopyWarning = showCopyWarning,
                    collapseAddress = label != null,
                )
            )
            contact?.let { add(TransactionInfoViewItem.ContactItem(it)) }
        }
    }

    private fun getNftAmount(
        title: String,
        value: TransactionValue.NftValue,
        incoming: Boolean?,
        hideAmount: Boolean,
    ): TransactionInfoViewItem {
        val valueFormatted = if (hideAmount) "*****" else value.decimalValue.let { decimalValue ->
            val sign = when {
                incoming == null -> ""
                decimalValue < BigDecimal.ZERO -> "-"
                decimalValue > BigDecimal.ZERO -> "+"
                else -> ""
            }
            val valueWithCoinCode =
                numberFormatter.formatCoinFull(decimalValue.abs(), value.coinCode, 8)
            "$sign$valueWithCoinCode"
        }

        return TransactionInfoViewItem.NftAmount(
            title,
            ColoredValue(valueFormatted, amountColor(incoming)),
            value.tokenName ?: "#${value.mintAddress}",
            null,
            Res.drawable.icon_24_nft_placeholder,
            null,
        )
    }

    private fun getAmount(
        rate: CurrencyValue?,
        value: TransactionValue,
        incoming: Boolean?,
        hideAmount: Boolean,
        amountType: AmountType,
        amount: EvmTransactionRecord.Amount? = null,
        hasRecipient: Boolean = false,
    ): TransactionInfoViewItem {
        val valueInFiat = if (hideAmount) "*****" else rate?.let {
            value.decimalValue?.let { decimalValue ->
                numberFormatter.formatFiatFull(
                    (it.value * decimalValue).abs(),
                    it.currency.symbol
                )
            }
        } ?: "---"
        val fiatValueColored = ColoredValue(valueInFiat, ColorName.Secondary)
        val coinValueFormatted =
            if (hideAmount) "*****" else value.decimalValue?.let { decimalValue ->
                val sign = when (incoming) {
                    true -> "+"
                    false -> "-"
                    else -> ""
                }
                val valueWithCoinCode =
                    numberFormatter.formatCoinFull(decimalValue.abs(), value.coinCode, 8)
                if (amount is EvmTransactionRecord.Amount.Extremum && incoming != null) {
                    val suffix =
                        if (incoming) Translator.getString(Res.string.Swap_AmountMin) else Translator.getString(
                            Res.string.Swap_AmountMax
                        )
                    "$sign$valueWithCoinCode $suffix"
                } else {
                    "$sign$valueWithCoinCode"
                }
            } ?: "---"

        val coinValueColored = ColoredValue(coinValueFormatted, amountColor(incoming, hasRecipient))
        val coinUid = if (value is TransactionValue.CoinValue && !value.token.isCustom) {
            value.token.coin.uid
        } else {
            null
        }
        return TransactionInfoViewItem.Amount(
            coinValueColored,
            fiatValueColored,
            value.coinIconUrl,
            value.alternativeCoinIconUrl,
            value.coinIconPlaceholder,
            coinUid,
            value.badge,
            amountType,
        )
    }

    private fun getHistoricalRate(
        rate: CurrencyValue?,
        transactionValue: TransactionValue,
    ): TransactionInfoViewItem {
        val rateValue = if (rate == null) {
            "---"
        } else {
            val rateFormatted = numberFormatter.formatFiatFull(rate.value, rate.currency.symbol)
            Translator.getString(
                Res.string.Balance_RatePerCoin,
                rateFormatted,
                transactionValue.coinCode
            )
        }
        return TransactionInfoViewItem.Value(
            Translator.getString(Res.string.TransactionInfo_HistoricalRate),
            rateValue
        )
    }

    fun getReceiveSectionItems(
        value: TransactionValue,
        fromAddress: String?,
        toAddress: List<String>?,
        changeAddresses: List<String>? = null,
        coinPrice: CurrencyValue?,
        hideAmount: Boolean,
        blockchainType: BlockchainType,
        amlItem: TransactionInfoViewItem.AmlCheck? = null,
        showCopyWarning: Boolean = false,
    ): List<TransactionInfoViewItem> {
        val mint = fromAddress == zeroAddress
        val title: String =
            if (mint) Translator.getString(Res.string.Transactions_Mint) else Translator.getString(
                Res.string.Transactions_Receive
            )

        val amount: TransactionInfoViewItem
        val rate: TransactionInfoViewItem?

        when (value) {
            is TransactionValue.NftValue -> {
                amount = getNftAmount(title, value, true, hideAmount)
                rate = null
            }

            else -> {
                amount = getAmount(coinPrice, value, true, hideAmount, AmountType.Received)
                rate = getHistoricalRate(coinPrice, value)
            }
        }

        val items: MutableList<TransactionInfoViewItem> = mutableListOf(
            amount
        )

        if (!mint && fromAddress != null) {
            items.addAll(
                getAddressItems(
                    title = Translator.getString(Res.string.TransactionInfo_From),
                    address = fromAddress,
                    blockchainType = blockchainType,
                    showCopyWarning = showCopyWarning,
                )
            )
        }

        toAddress?.forEach { address ->
            items.add(
                TransactionInfoViewItem.Address(
                    Translator.getString(Res.string.TransactionInfo_To),
                    address,
                    false,
                    blockchainType,
                )
            )
        }

        changeAddresses?.forEach {
            items.add(
                TransactionInfoViewItem.Address(
                    Translator.getString(Res.string.TransactionInfo_Change_Address),
                    it,
                    false,
                    blockchainType,
                )
            )
        }

        amlItem?.let { items.add(it) }
        rate?.let { items.add(it) }

        return items
    }


    fun getSendSectionItems(
        value: TransactionValue,
        toAddress: List<String>?,
        changeAddresses: List<String>? = null,
        coinPrice: CurrencyValue?,
        hideAmount: Boolean,
        sentToSelf: Boolean = false,
        blockchainType: BlockchainType,
        showCopyWarning: Boolean = false,
    ): List<TransactionInfoViewItem> {
        val burn = toAddress?.size == 1 && toAddress.first() == zeroAddress

        val title: String =
            if (burn) Translator.getString(Res.string.Transactions_Burn) else Translator.getString(
                Res.string.Transactions_Send
            )

        val amount: TransactionInfoViewItem
        val rate: TransactionInfoViewItem?

        when (value) {
            is TransactionValue.NftValue -> {
                amount = getNftAmount(
                    title,
                    value,
                    if (sentToSelf) null else false,
                    hideAmount
                )
                rate = null
            }

            else -> {
                amount = getAmount(
                    coinPrice,
                    value,
                    if (sentToSelf) null else false,
                    hideAmount,
                    AmountType.Sent
                )
                rate = getHistoricalRate(coinPrice, value)
            }
        }

        val items: MutableList<TransactionInfoViewItem> = mutableListOf(amount)

        if (!burn && toAddress != null) {
            toAddress.forEach { address ->
                items.addAll(
                    getAddressItems(
                        title = Translator.getString(Res.string.TransactionInfo_To),
                        address = address,
                        blockchainType = blockchainType,
                        showCopyWarning = showCopyWarning,
                    )
                )
            }
        }

        changeAddresses?.forEach {
            items.add(
                TransactionInfoViewItem.Address(
                    Translator.getString(Res.string.TransactionInfo_Change_Address),
                    it,
                    false,
                    blockchainType,
                )
            )
        }

        rate?.let { items.add(it) }

        return items
    }

    fun getSwapEventSectionItems(
        valueIn: TransactionValue?,
        valueOut: TransactionValue?,
        rates: Map<String, CurrencyValue>,
        amount: EvmTransactionRecord.Amount?,
        hideAmount: Boolean,
        hasRecipient: Boolean,
    ) = buildList {
        valueIn?.let {
            add(
                getAmount(
                    rates[valueIn.coinUid],
                    valueIn,
                    false,
                    hideAmount,
                    AmountType.YouSent,
                    amount
                )
            )
        }
        valueOut?.let {
            add(
                getAmount(
                    rates[valueOut.coinUid],
                    valueOut,
                    true,
                    hideAmount,
                    AmountType.YouGot,
                    hasRecipient = hasRecipient,
                )
            )
        }
    }

    fun getSwapDetailsSectionItems(
        rates: Map<String, CurrencyValue>,
        exchangeAddress: String,
        valueOut: TransactionValue?,
        valueIn: TransactionValue?,
        blockchainType: BlockchainType,
        providerName: String? = null,
    ): List<TransactionInfoViewItem> {
        val items: MutableList<TransactionInfoViewItem> = mutableListOf(
            TransactionInfoViewItem.Value(
                Translator.getString(Res.string.Swap_SwapProvider_Title),
                providerName ?: addressMetadataManager.mapped(blockchainType, exchangeAddress)
            )
        )

        val (priceValueOne, priceValueTwo) = swapPriceValues(valueIn, valueOut, rates)
            ?: return items

        items.add(
            TransactionInfoViewItem.PriceWithToggle(
                Translator.getString(Res.string.TransactionInfo_Price),
                priceValueOne,
                priceValueTwo,
            )
        )

        return items
    }

    private fun swapPriceValues(
        valueIn: TransactionValue?,
        valueOut: TransactionValue?,
        rates: Map<String, CurrencyValue>,
    ): Pair<String, String>? {
        val input = valueIn?.toSwapValue() ?: return null
        val output = valueOut?.toSwapValue() ?: return null
        return formatSwapPrice(output, input, rates[input.coinUid]) to
                formatSwapPrice(input, output, rates[output.coinUid])
    }

    private fun TransactionValue.toSwapValue(): SwapValue? {
        return SwapValue(
            decimalValue = decimalValue ?: return null,
            decimals = decimals ?: return null,
            coinCode = coinCode,
            coinUid = coinUid,
        )
    }

    private fun formatSwapPrice(
        base: SwapValue,
        quote: SwapValue,
        rate: CurrencyValue?,
    ): String {
        if (base.decimalValue.compareTo(BigDecimal.ZERO) == 0) {
            return Translator.getString(Res.string.NotAvailable)
        }

        val price = quote.decimalValue.divide(
            base.decimalValue,
            min(base.decimals, quote.decimals),
            RoundingMode.HALF_EVEN,
        ).abs()
        val formattedPrice = numberFormatter.formatCoinFull(price, quote.coinCode, 8)
        val formattedFiatPrice = rate?.let {
            " (${numberFormatter.formatFiatFull(price * it.value, it.currency.symbol)})"
        }.orEmpty()
        return "${base.coinCode} = $formattedPrice$formattedFiatPrice"
    }

    private data class SwapValue(
        val decimalValue: BigDecimal,
        val decimals: Int,
        val coinCode: String,
        val coinUid: String,
    )

    fun getContractCreationItems(transaction: EvmTransactionRecord): List<TransactionInfoViewItem> =
        listOf(
            TransactionInfoViewItem.Transaction(
                Translator.getString(Res.string.Transactions_ContractCreation),
                "",
                TransactionViewItem.Icon.Platform.fromBlockchainType(transaction.blockchainType).iconRes
            )
        )

    fun getApproveSectionItems(
        value: TransactionValue,
        coinPrice: CurrencyValue?,
        spenderAddress: String,
        hideAmount: Boolean,
        blockchainType: BlockchainType,
    ): List<TransactionInfoViewItem> {
        val fiatAmountFormatted = coinPrice?.let {
            value.decimalValue?.let { decimalValue ->
                numberFormatter.formatFiatFull(
                    (it.value * decimalValue).abs(),
                    it.currency.symbol
                )
            }
        } ?: "---"

        val coinAmountFormatted = value.decimalValue?.let { decimalValue ->
            numberFormatter.formatCoinFull(
                decimalValue,
                value.coinCode,
                8
            )
        } ?: ""

        val coinAmountString = when {
            hideAmount -> "*****"
            value.isMaxValue -> "∞ ${value.coinCode}"

            else -> coinAmountFormatted
        }

        val coinAmountColoredValue = ColoredValue(coinAmountString, amountColor(null))

        val fiatAmountString = when {
            hideAmount -> "*****"
            value.isMaxValue -> Translator.getString(Res.string.Transaction_Unlimited)
            else -> fiatAmountFormatted
        }

        val fiatAmountColoredValue = ColoredValue(fiatAmountString, ColorName.Secondary)

        val items: MutableList<TransactionInfoViewItem> = mutableListOf(
            TransactionInfoViewItem.Amount(
                coinAmountColoredValue,
                fiatAmountColoredValue,
                value.coinIconUrl,
                value.alternativeCoinIconUrl,
                value.coinIconPlaceholder,
                value.coin?.uid,
                value.badge,
                AmountType.Approved
            )
        )

        items.addAll(
            getAddressItems(
                title = Translator.getString(Res.string.TransactionInfo_Spender),
                address = spenderAddress,
                blockchainType = blockchainType,
            )
        )

        return items
    }

    fun getContractMethodSectionItems(
        method: String?,
        contractAddress: String,
        blockchainType: BlockchainType,
    ) = listOf(
        TransactionInfoViewItem.Transaction(
            method
                ?: Translator.getString(Res.string.Transactions_ContractCall),
            addressMetadataManager.mapped(blockchainType, contractAddress),
            TransactionViewItem.Icon.Platform.fromBlockchainType(blockchainType).iconRes
        )
    )

    fun getBitcoinSectionItems(
        transaction: BitcoinTransactionRecord,
        lastBlockInfo: LastBlockInfo?,
    ): List<TransactionInfoViewItem> {
        val items: MutableList<TransactionInfoViewItem> = mutableListOf()

        transaction.conflictingHash?.let { conflictingHash ->
            items.add(
                getDoubleSpendViewItem(
                    transaction.transactionHash,
                    conflictingHash
                )
            )
        }

        if (transaction.showRawTransaction) {
            items.add(TransactionInfoViewItem.RawTransaction)
        }

        val lockState = transaction.lockState(lastBlockInfo?.timestamp)
        getLockStateItem(lockState)?.let {
            items.add(it)
        }

        return items
    }

    private fun getBeamStatusItems(
        transaction: BeamTransactionRecord,
        rate: CurrencyValue?,
        hideSensitiveInfo: Boolean,
    ): List<TransactionInfoViewItem> = buildList {
        // A receiving wallet never pays this fee: the row would report the sender's cost, or nothing
        // at all on a shielded receive. Neither belongs in the user's own status section.
        if (transaction.direction != BeamTransactionDirection.Incoming) {
            val fee = getFeeItem(transaction.fee, rate, transaction.status(null))
            add(TransactionInfoViewItem.Value(fee.title, fee.value.orHide(hideSensitiveInfo)))
        }
        listOf(
            Res.string.beam_history_kernel_id to transaction.kernelId,
            Res.string.beam_history_failure_reason to transaction.failureReasonText,
        ).forEach { (title, value) ->
            value?.takeIf { it.isNotBlank() }?.let {
                add(TransactionInfoViewItem.Value(Translator.getString(title), it.orHide(hideSensitiveInfo)))
            }
        }
    }

    fun getStatusSectionItems(
        transaction: TransactionRecord,
        status: TransactionStatus,
        rates: Map<String, CurrencyValue?>,
        blockchainType: BlockchainType,
        hideSensitiveInfo: Boolean
    ): List<TransactionInfoViewItem> {
        val items: MutableList<TransactionInfoViewItem> = mutableListOf(
            TransactionInfoViewItem.Value(
                Translator.getString(Res.string.TransactionInfo_Date),
                DateHelper.getFullDate(Date(transaction.timestamp * 1000))
            ),
            if (transaction is BeamTransactionRecord) {
                TransactionInfoViewItem.Value(
                    Translator.getString(Res.string.TransactionInfo_Status),
                    Translator.getString(transaction.statusTitle),
                )
            } else {
                TransactionInfoViewItem.Status(status)
            }
        )

        when (transaction) {
            is BeamTransactionRecord -> items.addAll(
                getBeamStatusItems(transaction, rates[transaction.fee.coinUid], hideSensitiveInfo)
            )

            is EvmTransactionRecord -> {
                val fee = transaction.fee
                if (!transaction.foreignTransaction && fee != null) {
                    items.add(getFeeItem(fee, rates[fee.coinUid], status))
                }

                // Show sync source if available (for debugging/transparency)
                transaction.syncSource?.let { source ->
                    items.add(
                        TransactionInfoViewItem.Value(
                            Translator.getString(Res.string.transaction_info_sync_source),
                            source
                        )
                    )
                }

                if (transaction.transactionRecordType == TransactionRecordType.EVM_SWAP && transaction.valueOut !=
                    null) {
                    val recipientItems = mutableListOf<TransactionInfoViewItem>()

                    val recipient = transaction.recipient
                    if (recipient != null) {
                        recipientItems.addAll(
                            getAddressItems(
                                title = Translator.getString(Res.string.TransactionInfo_RecipientHash),
                                address = recipient,
                                blockchainType = blockchainType,
                            )
                        )
                    }

                    items.addAll(0, recipientItems)
                }
            }

            is TronTransactionRecord -> {
                val fee = transaction.fee
                if (!transaction.foreignTransaction && fee != null) {
                    items.add(getFeeItem(fee, rates[fee.coinUid], status))
                }
            }

            is StellarTransactionRecord -> {
                transaction.fee?.let { fee ->
                    items.add(getFeeItem(fee, rates[fee.coinUid], status))
                }
            }

            is TonTransactionRecord -> {
                items.add(getFeeItem(transaction.fee, rates[transaction.fee.coinUid], status))
            }

            is ThorchainTransactionRecord -> {
                transaction.fee?.let { fee ->
                    items.add(getFeeItem(fee, rates[fee.coinUid], status))
                }
            }

            is BitcoinTransactionRecord -> {
                if (transaction.transactionRecordType == TransactionRecordType.BITCOIN_OUTGOING) {
                    transaction.fee?.let { items.add(getFee(it, rates[it.coinUid])) }
                }
            }

            is SolanaTransactionRecord -> {
                val fee = transaction.fee
                if (transaction.transactionRecordType == TransactionRecordType.SOLANA_OUTGOING && fee != null) {
                    items.add(getFeeItem(fee, rates[fee.coinUid], status))
                }
            }

            is MoneroTransactionRecord -> {
                items.add(getFeeItem(transaction.fee, rates[transaction.fee.coinUid], status))
            }
        }

        items.add(
            TransactionInfoViewItem.TransactionHash(
                transaction.transactionHash.orHide(
                    hideSensitiveInfo
                )
            )
        )

        return items
    }

    fun getExplorerSectionItems(explorerData: List<TransactionInfoModule.ExplorerData>): List<TransactionInfoViewItem> =
        explorerData.mapNotNull { getExplorerSectionItem(it) }

    fun getExplorerSectionItems(explorerData: TransactionInfoModule.ExplorerData): List<TransactionInfoViewItem> =
        listOfNotNull(getExplorerSectionItem(explorerData))

    private fun getExplorerSectionItem(explorerData: TransactionInfoModule.ExplorerData): TransactionInfoViewItem? {
        val url = explorerData.url ?: return null
        return TransactionInfoViewItem.Explorer(
            title = Translator.getString(
                Res.string.TransactionInfo_ButtonViewOnExplorerName,
                explorerData.title
            ),
            url = url,
        )
    }

    fun getSwapProviderAmountSectionItems(
        amountOut: BigDecimal,
        amountOutReal: BigDecimal?,
        amountIn: BigDecimal,
        coinCodeOut: String,
        coinCodeIn: String,
        hideAmount: Boolean
    ): List<TransactionInfoViewItem> {
        val items = mutableListOf<TransactionInfoViewItem>()

        // Announced Amount - sensitive, clickable
        val announcedAmountFormatted = if (hideAmount) {
            "*****"
        } else {
            numberFormatter.formatCoinNotRounded(amountOut, coinCodeOut, 8)
        }
        items.add(
            TransactionInfoViewItem.ValueClickable(
                Translator.getString(Res.string.TransactionInfo_AnnouncedAmount),
                announcedAmountFormatted
            )
        )

        // Expected Amount (only if available) - sensitive, clickable
        amountOutReal?.let { real ->
            val executedAmountFormatted = if (hideAmount) {
                "*****"
            } else {
                numberFormatter.formatCoinNotRounded(real, coinCodeOut, 8)
            }
            items.add(
                TransactionInfoViewItem.ValueClickable(
                    Translator.getString(Res.string.transaction_info_executed_amount),
                    executedAmountFormatted
                )
            )

            // Price Impact - not sensitive
            if (amountOut > BigDecimal.ZERO) {
                val impact = tryOrNull {
                    (real - amountOut).divide(
                        amountOut,
                        4,
                        RoundingMode.HALF_UP
                    ) * BigDecimal(100)
                } ?: BigDecimal.ZERO
                val impactStr = "${if (impact >= BigDecimal.ZERO) "+" else ""}${
                    impact.setScale(
                        2,
                        RoundingMode.HALF_UP
                    )
                }%"
                val color = if (impact >= BigDecimal.ZERO) ColorName.Remus else ColorName.Lucian
                items.add(
                    TransactionInfoViewItem.ValueColored(
                        Translator.getString(Res.string.TransactionInfo_PriceImpact),
                        impactStr,
                        color
                    )
                )
            }

            // Executed Rate: 1 coinCodeIn = X coinCodeOut - not sensitive
            if (amountIn > BigDecimal.ZERO) {
                val executedRate = real.divide(amountIn, 8, RoundingMode.HALF_UP)
                val formattedRate = numberFormatter.formatCoinFull(executedRate, coinCodeOut, 8)
                items.add(
                    TransactionInfoViewItem.Value(
                        Translator.getString(Res.string.TransactionInfo_ExecutedRate),
                        "1 $coinCodeIn = $formattedRate"
                    )
                )
            }
        }

        return items
    }

    fun getAmlCheckItem(
        amlStatus: AmlStatus?,
        record: TransactionRecord,
        blockchainType: BlockchainType
    ): TransactionInfoViewItem.AmlCheck? {
        if (amlStatus == null) return null
        if (!record.isIncomingForAmlCheck()) return null

        val senderAddresses = record.getSenderAddresses()
        if (senderAddresses.isEmpty()) return null

        return TransactionInfoViewItem.AmlCheck(
            status = amlStatus,
            senderAddresses = senderAddresses,
            blockchainType = blockchainType
        )
    }

}
