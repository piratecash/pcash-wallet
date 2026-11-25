package cash.p.terminal.modules.transactions

import org.jetbrains.compose.resources.StringResource
import cash.p.beam.BeamTransactionDirection
import cash.p.terminal.entities.transactionrecords.beam.BeamTransactionRecord
import cash.p.terminal.core.TransactionSettingsStorage
import cash.p.terminal.core.managers.BalanceHiddenManager
import cash.p.terminal.core.managers.AddressMetadataManager
import cash.p.terminal.core.managers.PoisonAddressManager
import cash.p.terminal.core.storage.SwapProviderTransactionsStorage
import cash.p.terminal.core.usecase.RelinkSwapToReplacementUseCase
import cash.p.terminal.core.utils.SwapTransactionMatcher
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.PendingTransactionRecord
import cash.p.terminal.entities.transactionrecords.TransactionRecordType
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.EvmTransactionRecord
import cash.p.terminal.entities.transactionrecords.evm.TransferEvent
import cash.p.terminal.entities.transactionrecords.monero.MoneroTransactionRecord
import cash.p.terminal.entities.transactionrecords.solana.SolanaTransactionRecord
import cash.p.terminal.entities.transactionrecords.stellar.StellarTransactionRecord
import cash.p.terminal.entities.transactionrecords.thorchain.ThorchainTransactionRecord
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.entities.transactionrecords.tron.TronTransactionRecord
import cash.p.terminal.entities.UnstoppableProviderTitles
import cash.p.terminal.entities.swapProviderDisplayTitle
import cash.p.terminal.entities.toStatusUrl
import cash.p.terminal.modules.paycore.PayCoreAssetResolver
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.changenow.domain.entity.toStatus
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.coin_placeholder
import cash.p.terminal.resources.icon_24_nft_placeholder
import io.horizontalsystems.core.Translator
import cash.p.terminal.strings.helpers.shorten
import cash.p.terminal.ui_compose.ColorName
import cash.p.terminal.ui_compose.ColoredValue
import cash.p.terminal.ui_compose.amountColor
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.alternativeImageUrl
import cash.p.terminal.wallet.coinImageUrl
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.CurrencyValue
import io.horizontalsystems.core.helpers.DateHelper
import java.math.BigDecimal
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import org.koin.java.KoinJavaComponent.inject
import org.jetbrains.compose.resources.DrawableResource
import cash.p.terminal.resources.Transaction_Unlimited_CoinCode
import cash.p.terminal.resources.Transactions_From
import cash.p.terminal.resources.Transactions_Multiple
import cash.p.terminal.resources.Transactions_To
import cash.p.terminal.resources.Transactions_Unknown_Description
import cash.p.terminal.resources.ic_migrate_24
import cash.p.terminal.resources.transactions_account_delegated_to
import cash.p.terminal.resources.transactions_account_delegation_warning_short
import cash.p.terminal.resources.transactions_migrate_to_ironwood

class TransactionViewItemFactory(
    private val addressMetadataManager: AddressMetadataManager,
    private val balanceHiddenManager: BalanceHiddenManager,
    private val swapProviderTransactionsStorage: SwapProviderTransactionsStorage,
    private val swapTransactionMatcher: SwapTransactionMatcher,
    private val numberFormatter: IAppNumberFormatter,
    private val marketKit: MarketKitWrapper,
    private val localStorage: TransactionSettingsStorage,
    private val poisonAddressManager: PoisonAddressManager,
    private val unstoppableProviderTitles: UnstoppableProviderTitles,
) {
    private data class CacheKey(
        val transactionItemVersion: Long,
        val matchedSwapStatus: String?,
    )

    private val swapProviderRecordResolver = SwapProviderRecordResolver(
        swapProviderTransactionsStorage,
        swapTransactionMatcher
    )
    private val relinkSwapToReplacement: RelinkSwapToReplacementUseCase
            by inject(RelinkSwapToReplacementUseCase::class.java)

    private var showAmount = !balanceHiddenManager.balanceHidden
    private var addressPoisoningViewMode = localStorage.addressPoisoningViewMode
    private val cache = ConcurrentHashMap<String, Map<CacheKey, TransactionViewItem>>()

    fun updateCache() {
        showAmount = !balanceHiddenManager.balanceHidden
        addressPoisoningViewMode = localStorage.addressPoisoningViewMode
        for (uid in cache.keys) {
            cache.computeIfPresent(uid) { _, map ->
                val perItemShowAmount = !balanceHiddenManager.isTransactionInfoHidden(uid)
                map.mapValues { (_, viewItem) ->
                    viewItem.copy(showAmount = perItemShowAmount, addressPoisoningViewMode = addressPoisoningViewMode)
                }
            }
        }
    }

    fun clearCache() {
        cache.clear()
    }

    suspend fun convertToViewItemCached(
        transactionItem: TransactionItem,
        walletUid: String? = null,
        matchedSwap: SwapProviderTransaction? = null
    ): TransactionViewItem {
        val swap = (transactionItem.record as? BitcoinTransactionRecord)
            ?.let { relinkReplacementChainSwap(it) } ?: matchedSwap
        val perItemShowAmount = if (walletUid != null) {
            !balanceHiddenManager.isTransactionInfoHiddenForWallet(
                transactionItem.record.uid,
                walletUid
            )
        } else {
            !balanceHiddenManager.isTransactionInfoHidden(transactionItem.record.uid)
        }

        val cacheKey = CacheKey(
            transactionItemVersion = transactionItem.cacheVersion,
            matchedSwapStatus = swap?.status,
        )

        cache[transactionItem.record.uid]?.get(cacheKey)?.let { cached ->
            return if (cached.showAmount != perItemShowAmount || cached.addressPoisoningViewMode !=
                addressPoisoningViewMode) {
                cached.copy(
                    showAmount = perItemShowAmount,
                    addressPoisoningViewMode = addressPoisoningViewMode,
                ).also {
                    cache[transactionItem.record.uid] = mapOf(cacheKey to it)
                }
            } else {
                cached
            }
        }

        val convertedViewItem = convertToViewItem(transactionItem, swap)
        val transactionViewItem = convertedViewItem.copy(
            showAmount = perItemShowAmount,
            addressPoisoningViewMode = addressPoisoningViewMode,
            poisonStatus = poisonAddressManager.getPoisonStatus(transactionItem.record),
            isSwap = transactionItem.record.isSwap() || convertedViewItem.changeNowTransactionId != null,
        )
        cache[transactionItem.record.uid] = mapOf(cacheKey to transactionViewItem)

        return transactionViewItem
    }

    private fun singleValueIconType(
        value: TransactionValue
    ): TransactionViewItem.Icon =
        when (value) {
            is TransactionValue.NftValue -> {
                TransactionViewItem.Icon.Regular(
                    null,
                    null,
                    Res.drawable.icon_24_nft_placeholder,
                    rectangle = true
                )
            }

            is TransactionValue.CoinValue,
            is TransactionValue.RawValue,
            is TransactionValue.JettonValue,
            is TransactionValue.TokenValue -> {
                TransactionViewItem.Icon.Regular(
                    value.coinIconUrl,
                    value.alternativeCoinIconUrl,
                    value.coinIconPlaceholder
                )
            }
        }

    private fun doubleValueIconType(
        primaryValue: TransactionValue?,
        secondaryValue: TransactionValue?
    ): TransactionViewItem.Icon {
        var backUrl: String? = null
        var backAlternativeUrl: String? = null
        var backPlaceHolder: DrawableResource? = null
        var backRectangle = false
        var frontUrl: String? = null
        var frontAlternativeUrl: String? = null
        var frontPlaceHolder: DrawableResource? = null
        var frontRectangle = false

        if (primaryValue != null) {
            when (primaryValue) {
                is TransactionValue.NftValue -> {
                    frontRectangle = true
                    frontPlaceHolder = Res.drawable.icon_24_nft_placeholder
                }

                is TransactionValue.CoinValue,
                is TransactionValue.RawValue,
                is TransactionValue.JettonValue,
                is TransactionValue.TokenValue -> {
                    frontRectangle = false
                    frontUrl = primaryValue.coinIconUrl
                    frontAlternativeUrl = primaryValue.alternativeCoinIconUrl
                    frontPlaceHolder = primaryValue.coinIconPlaceholder
                }
            }
        } else {
            frontRectangle = false
            frontUrl = null
            frontPlaceHolder = Res.drawable.coin_placeholder
        }

        if (secondaryValue != null) {
            when (secondaryValue) {
                is TransactionValue.NftValue -> {
                    backRectangle = true
                    backPlaceHolder = Res.drawable.icon_24_nft_placeholder
                }

                is TransactionValue.CoinValue,
                is TransactionValue.RawValue,
                is TransactionValue.JettonValue,
                is TransactionValue.TokenValue -> {
                    backRectangle = false
                    backUrl = secondaryValue.coinIconUrl
                    backAlternativeUrl = secondaryValue.alternativeCoinIconUrl
                    backPlaceHolder = secondaryValue.coinIconPlaceholder
                }
            }
        } else {
            backRectangle = false
            backUrl = null
            backPlaceHolder = Res.drawable.coin_placeholder
        }

        return TransactionViewItem.Icon.Double(
            back = TransactionViewItem.Icon.Regular(
                backUrl,
                backAlternativeUrl,
                backPlaceHolder,
                backRectangle
            ),
            front = TransactionViewItem.Icon.Regular(
                frontUrl,
                frontAlternativeUrl,
                frontPlaceHolder,
                frontRectangle
            )
        )
    }

    private fun getIconForToken(
        coinUid: String
    ): TransactionViewItem.Icon.Regular {
        val coin = marketKit.coin(coinUid)
        return TransactionViewItem.Icon.Regular(
            url = coinImageUrl(coinUid),
            alternativeUrl = coin?.alternativeImageUrl,
            placeholder = Res.drawable.coin_placeholder
        )
    }

    private fun iconType(
        blockchainType: BlockchainType,
        incomingValues: List<TransactionValue>,
        outgoingValues: List<TransactionValue>
    ): TransactionViewItem.Icon = when {
        incomingValues.size == 1 && outgoingValues.isEmpty() -> {
            singleValueIconType(incomingValues[0])
        }

        incomingValues.isEmpty() && outgoingValues.size == 1 -> {
            singleValueIconType(outgoingValues[0])
        }

        incomingValues.size == 1 && outgoingValues.size == 1 -> {
            doubleValueIconType(incomingValues[0], outgoingValues[0])
        }

        else -> {
            TransactionViewItem.Icon.Platform.fromBlockchainType(blockchainType)
        }
    }

    private fun createViewItemFromEvmTransactionRecord(
        record: EvmTransactionRecord,
        transactionItem: TransactionItem,
        progress: Float?,
        icon: TransactionViewItem.Icon?,
    ): TransactionViewItem = when (record.transactionRecordType) {
        TransactionRecordType.EVM_APPROVE ->
            createViewItemFromApproveTransactionRecord(
                transactionItem = transactionItem,
                value = record.value!!,
                spender = record.spender!!,
                progress = progress,
                icon = icon
            )

        TransactionRecordType.EVM_CONTRACT_CALL -> {
            val (incomingValues, outgoingValues) = EvmTransactionRecord.combined(
                incomingEvents = record.incomingEvents!!,
                outgoingEvents = record.outgoingEvents!!
            )
            createViewItemFromContractCallTransactionRecord(
                transactionItem = transactionItem,
                incomingValues = incomingValues,
                outgoingValues = outgoingValues,
                contractAddress = record.contractAddress!!,
                progress = progress,
                icon = icon,
            ).let { viewItem ->
                if (record.liquidityOperation == null) {
                    viewItem
                } else {
                    val (primary, secondary) = getLiquidityValues(
                        incomingValues,
                        outgoingValues,
                        transactionItem.currencyValue
                    )
                    viewItem.copy(primaryValue = primary, secondaryValue = secondary)
                }
            }
        }

        TransactionRecordType.EVM_EXTERNAL_CONTRACT_CALL -> {
            val incomingEvents = checkNotNull(record.incomingEvents)
            val outgoingEvents = checkNotNull(record.outgoingEvents)
            val (incomingValues, outgoingValues) = EvmTransactionRecord.combined(
                incomingEvents = incomingEvents,
                outgoingEvents = outgoingEvents
            )
            createViewItemFromExternalContractCallTransactionRecord(
                transactionItem = transactionItem,
                incomingValues = incomingValues,
                outgoingValues = outgoingValues,
                incomingEvents = incomingEvents,
                outgoingEvents = outgoingEvents,
                progress = progress,
                icon = icon
            )
        }

        TransactionRecordType.EVM_CONTRACT_CREATION -> createViewItemFromContractCreationTransactionRecord(
            record = record,
            progress = progress,
            icon = icon
        )

        TransactionRecordType.EVM_INCOMING ->
            createViewItemFromEvmIncomingTransactionRecord(
                transactionItem = transactionItem,
                value = record.value!!,
                from = record.from!!,
                progress = progress,
                icon = icon
            )

        TransactionRecordType.EVM_OUTGOING ->
            createViewItemFromEvmOutgoingTransactionRecord(
                transactionItem = transactionItem,
                value = record.value!!,
                to = record.to?.firstOrNull()!!,
                sentToSelf = record.sentToSelf,
                progress = progress,
                icon = icon
            )

        TransactionRecordType.EVM_SWAP -> createViewItemFromSwapTransactionRecord(
            record = record,
            progress = progress,
            icon = icon
        )

        TransactionRecordType.EVM_UNKNOWN_SWAP -> createViewItemFromUnknownSwapTransactionRecord(
            record = record,
            progress = progress,
            icon = icon
        )

        TransactionRecordType.EVM -> createViewItemFromEvmTransactionRecord(
            uid = record.uid,
            title = record.listTitle(),
            timestamp = record.timestamp,
            blockchainType = record.blockchainType,
            progress = progress,
            spam = record.spam,
            icon = icon
        )

        TransactionRecordType.EVM_ACCOUNT_DELEGATION -> createViewItemFromDelegationTransactionRecord(
            record = record,
            progress = progress,
            icon = icon
        )

        else -> throw IllegalStateException("Undefined record type ${record.javaClass.name}")
    }

    private fun convertToViewItem(
        transactionItem: TransactionItem,
        matchedSwap: SwapProviderTransaction? = null
    ): TransactionViewItem {
        val record = transactionItem.record
        val status = record.status(transactionItem.lastBlockInfo?.height)
        val progress = when (status) {
            is TransactionStatus.Pending -> 0.15f
            is TransactionStatus.Processing -> status.progress
            else -> null
        }
        val icon = if (status is TransactionStatus.Failed) TransactionViewItem.Icon.Failed else null

        val lastBlockTimestamp = transactionItem.lastBlockInfo?.timestamp

        return userSwapProviderViewItem(transactionItem, matchedSwap, progress) ?: when (record) {
            is EvmTransactionRecord -> createViewItemFromEvmTransactionRecord(
                record = record,
                transactionItem = transactionItem,
                progress = progress,
                icon = icon,
            )

            is BitcoinTransactionRecord ->
                createViewItemFromBitcoinTransactionRecord(
                    record = record,
                    currencyValue = transactionItem.currencyValue,
                    progress = progress,
                    lastBlockTimestamp = lastBlockTimestamp,
                    icon = icon,
                )

            is SolanaTransactionRecord -> createViewItemFromSolanaTransactionRecord(
                record = record,
                transactionItem = transactionItem,
                progress = progress,
                icon = icon,
            )

            is TronTransactionRecord -> createViewItemFromTronTransactionRecord(
                transactionItem = transactionItem,
                value = record.value,
                sentToSelf = record.sentToSelf,
                to = record.to?.firstOrNull(),
                from = record.from,
                spender = record.spender,
                progress = progress,
                contractAddress = record.contractAddress,
                incomingEvents = record.incomingEvents,
                outgoingEvents = record.outgoingEvents,
                icon = icon,
            )

            is TonTransactionRecord -> createViewItemFromTonTransactionRecord(
                icon = icon,
                record = record,
                currencyValue = transactionItem.currencyValue,
                progress = progress
            )

            is StellarTransactionRecord -> createViewItemFromStellarTransactionRecord(
                icon = icon,
                record = record,
                currencyValue = transactionItem.currencyValue
            )

            is ThorchainTransactionRecord -> createViewItemFromThorchainTransactionRecord(
                record = record,
                currencyValue = transactionItem.currencyValue,
                progress = progress,
                icon = icon
            )

            is BeamTransactionRecord -> createViewItemFromBeamTransactionRecord(
                record, transactionItem.currencyValue, progress, icon
            )

            is MoneroTransactionRecord -> createViewItemFromMoneroTransactionRecord(
                record = record,
                transactionItem = transactionItem,
                progress = progress,
                icon = icon
            )

            is PendingTransactionRecord -> createViewItemFromPendingTransactionRecord(
                record = record,
                icon = icon,
                currencyValue = transactionItem.currencyValue
            )

            else -> throw IllegalArgumentException("Undefined record type ${record.javaClass.name}")
        }
    }

    private fun createViewItemFromBeamTransactionRecord(
        record: BeamTransactionRecord,
        currencyValue: CurrencyValue?,
        progress: Float?,
        icon: TransactionViewItem.Icon?,
    ): TransactionViewItem {
        val color = when (record.direction) {
            BeamTransactionDirection.Incoming -> ColorName.Remus
            BeamTransactionDirection.Outgoing -> ColorName.Lucian
            BeamTransactionDirection.Self -> ColorName.Primary
        }
        return TransactionViewItem(
            uid = record.uid,
            progress = progress,
            title = record.listTitle(),
            // The status is already carried by the progress ring, so the subtitle names the other
            // party instead; mapped() resolves a contact or label and otherwise shortens the token.
            subtitle = record.counterparty?.let {
                Translator.getString(
                    if (record.direction == BeamTransactionDirection.Incoming) {
                        Res.string.Transactions_From
                    } else {
                        Res.string.Transactions_To
                    },
                    mapped(it, record.blockchainType),
                )
            } ?: "---",
            // Every other chain hides the sign when a transfer goes to the user's own wallet:
            // the balance does not move, so a minus would be a lie.
            primaryValue = getColoredValue(
                record.mainValue,
                color,
                hideSign = record.sentToSelf,
            ),
            secondaryValue = currencyValue?.let { getColoredValue(it, ColorName.Secondary) },
            showAmount = showAmount,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            sentToSelf = record.sentToSelf,
            spam = record.spam,
            icon = icon ?: singleValueIconType(record.mainValue),
        )
    }

    private fun createViewItemFromMoneroTransactionRecord(
        record: MoneroTransactionRecord,
        transactionItem: TransactionItem,
        progress: Float?,
        icon: TransactionViewItem.Icon?
    ): TransactionViewItem {
        return when (record.transactionRecordType) {
            TransactionRecordType.MONERO_INCOMING ->
                createViewItemFromMoneroIncomingTransactionRecord(
                    record = record,
                    currencyValue = transactionItem.currencyValue,
                    progress = progress,
                    icon = icon
                )

            TransactionRecordType.MONERO_OUTGOING ->
                createViewItemFromMoneroOutgoingTransactionRecord(
                    record = record,
                    currencyValue = transactionItem.currencyValue,
                    progress = progress,
                    icon = icon
                )

            else -> throw IllegalArgumentException("Undefined Monero record type ${record.transactionRecordType}")
        }
    }

    private fun createViewItemFromMoneroIncomingTransactionRecord(
        record: MoneroTransactionRecord,
        currencyValue: CurrencyValue?,
        progress: Float?,
        icon: TransactionViewItem.Icon?
    ): TransactionViewItem {
        val primaryValue = getColoredValue(record.mainValue, ColorName.Remus)
        val secondaryValue = currencyValue?.let {
            getColoredValue(it, ColorName.Secondary)
        }

        val subtitle = record.from?.let { from ->
            Translator.getString(
                Res.string.Transactions_From,
                mapped(from, record.blockchainType)
            )
        } ?: record.subaddressLabel ?: "---"

        return TransactionViewItem(
            uid = record.uid,
            progress = progress,
            title = record.listTitle(),
            subtitle = subtitle,
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            showAmount = showAmount,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            spam = record.spam,
            icon = icon ?: singleValueIconType(record.mainValue)
        )
    }

    private fun createViewItemFromMoneroOutgoingTransactionRecord(
        record: MoneroTransactionRecord,
        currencyValue: CurrencyValue?,
        progress: Float?,
        icon: TransactionViewItem.Icon?
    ): TransactionViewItem {
        val primaryValue = if (record.sentToSelf) {
            ColoredValue(getCoinString(record.mainValue, true), ColorName.Primary)
        } else {
            getColoredValue(record.mainValue, ColorName.Lucian)
        }

        val secondaryValue = currencyValue?.let {
            getColoredValue(it, ColorName.Secondary)
        }

        val subtitle = record.to?.let { to ->
            Translator.getString(
                Res.string.Transactions_To,
                mapped(to.first(), record.blockchainType)
            )
        } ?: record.subaddressLabel ?: "---"

        return TransactionViewItem(
            uid = record.uid,
            progress = progress,
            title = record.listTitle(),
            subtitle = subtitle,
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            showAmount = showAmount,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            sentToSelf = record.sentToSelf,
            spam = record.spam,
            icon = icon ?: singleValueIconType(record.mainValue)
        )
    }

    private fun createTonLiquidityViewItem(
        icon: TransactionViewItem.Icon?,
        record: TonTransactionRecord,
        currencyValue: CurrencyValue?,
        progress: Float?,
    ): TransactionViewItem {
        val (incomingValues, outgoingValues) = record.liquidityValues()
        val (primaryValue, secondaryValue) = getLiquidityValues(incomingValues, outgoingValues, currencyValue)

        return TransactionViewItem(
            uid = record.uid,
            progress = progress,
            title = record.listTitle(),
            subtitle = record.liquidityProtocol ?: Translator.getString(Res.string.Transactions_Multiple),
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            showAmount = showAmount,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            icon = icon ?: iconType(record.blockchainType, incomingValues, outgoingValues)
        )
    }

    private fun createViewItemFromTonTransactionRecord(
        icon: TransactionViewItem.Icon?,
        record: TonTransactionRecord,
        currencyValue: CurrencyValue?,
        progress: Float?,
    ): TransactionViewItem {
        if (record.liquidityOperation != null) {
            return createTonLiquidityViewItem(icon, record, currencyValue, progress)
        }

        val subtitle: String?
        val primaryValue: ColoredValue?
        var secondaryValue = currencyValue?.let {
            getColoredValue(it, ColorName.Secondary)
        }

        val iconX: TransactionViewItem.Icon
        var sentToSelf = false

        val action = record.actions.singleOrNull()

        if (action != null) {
            when (val actionType = action.type) {
                is TonTransactionRecord.Action.Type.Send -> {
                    subtitle = Translator.getString(
                        Res.string.Transactions_To,
                        mapped(actionType.to, record.blockchainType)
                    )

                    primaryValue = tonTransferColoredValue(actionType.value, ColorName.Lucian)

                    sentToSelf = actionType.sentToSelf

                    iconX = singleValueIconType(actionType.value)
                }

                is TonTransactionRecord.Action.Type.Receive -> {
                    subtitle = Translator.getString(
                        Res.string.Transactions_From,
                        mapped(actionType.from, record.blockchainType)
                    )

                    primaryValue = tonTransferColoredValue(actionType.value, ColorName.Remus)
                    iconX = singleValueIconType(actionType.value)
                }

                is TonTransactionRecord.Action.Type.Unsupported -> {
                    subtitle = actionType.type
                    primaryValue = null
                    secondaryValue = null


                    iconX = TransactionViewItem.Icon.Platform.fromBlockchainType(record.blockchainType)
                }

                is TonTransactionRecord.Action.Type.LiquidityDeposit -> {
                    subtitle = actionType.protocol.orEmpty()
                    primaryValue = null
                    secondaryValue = null
                    iconX = TransactionViewItem.Icon.Platform.fromBlockchainType(record.blockchainType)
                }

                is TonTransactionRecord.Action.Type.Burn -> {
                    iconX = singleValueIconType(actionType.value)
                    subtitle = actionType.value.fullName
                    primaryValue = getColoredValue(actionType.value, ColorName.Lucian)
                }

                is TonTransactionRecord.Action.Type.ContractCall -> {
                    iconX = TransactionViewItem.Icon.Platform.fromBlockchainType(record.blockchainType)
                    subtitle = actionType.address.shorten()
                    primaryValue = getColoredValue(actionType.value, ColorName.Lucian)
                }

                is TonTransactionRecord.Action.Type.ContractDeploy -> {
                    iconX = TransactionViewItem.Icon.Platform.fromBlockchainType(record.blockchainType)
                    subtitle = actionType.interfaces.joinToString()
                    primaryValue = null
                }

                is TonTransactionRecord.Action.Type.Mint -> {
                    iconX = singleValueIconType(actionType.value)
                    subtitle = actionType.value.fullName
                    primaryValue = getColoredValue(actionType.value, ColorName.Remus)
                }

                is TonTransactionRecord.Action.Type.Swap -> {
                    iconX = doubleValueIconType(actionType.valueOut, actionType.valueIn)
                    subtitle = actionType.routerName ?: actionType.routerAddress.shorten()
                    primaryValue = getColoredValue(actionType.valueOut, ColorName.Remus)
                    secondaryValue = getColoredValue(actionType.valueIn, ColorName.Lucian)
                }
            }
        } else {
            iconX = TransactionViewItem.Icon.Platform.fromBlockchainType(record.blockchainType)
            subtitle = Translator.getString(Res.string.Transactions_Multiple)
            primaryValue = null
        }

        return TransactionViewItem(
            uid = record.uid,
            progress = null,
            title = record.listTitle(),
            subtitle = subtitle,
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            showAmount = showAmount,
            sentToSelf = sentToSelf,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            icon = icon ?: iconX
        )
    }

    private fun createViewItemFromStellarTransactionRecord(
        icon: TransactionViewItem.Icon.Failed?,
        record: StellarTransactionRecord,
        currencyValue: CurrencyValue?,
    ): TransactionViewItem {

        val iconX: TransactionViewItem.Icon
        val subtitle: String
        val primaryValue: ColoredValue?
        var secondaryValue = currencyValue?.let {
            getColoredValue(it, ColorName.Secondary)
        }
        var sentToSelf = false

        when (val recordType = record.type) {
            is StellarTransactionRecord.Type.Send -> {
                subtitle = recordType.to?.let {
                    Translator.getString(Res.string.Transactions_To, mapped(it, record.blockchainType))
                } ?: "---"

                sentToSelf = recordType.sentToSelf

                primaryValue = if (sentToSelf) {
                    ColoredValue(getCoinString(recordType.value, true), ColorName.Secondary)
                } else {
                    getColoredValue(recordType.value, getAmountColorForSend(icon))
                }

                iconX = singleValueIconType(recordType.value)

            }

            is StellarTransactionRecord.Type.Receive -> {
                subtitle = recordType.from?.let {
                    Translator.getString(Res.string.Transactions_From, mapped(it, record.blockchainType))
                } ?: "---"

                primaryValue = getColoredValue(recordType.value, ColorName.Remus)
                iconX = singleValueIconType(recordType.value)
            }

            is StellarTransactionRecord.Type.ChangeTrust -> {
                subtitle = recordType.trustee.shorten()
                primaryValue = getColoredValue(recordType.value, ColorName.Primary, true)
                iconX = singleValueIconType(recordType.value)
            }

            is StellarTransactionRecord.Type.Swap -> {
                subtitle = ""
                primaryValue = getColoredValue(recordType.valueOut, ColorName.Remus)
                secondaryValue = getColoredValue(recordType.valueIn, ColorName.Lucian)
                iconX = doubleValueIconType(recordType.valueOut, recordType.valueIn)
            }

            is StellarTransactionRecord.Type.Unsupported -> {
                iconX = TransactionViewItem.Icon.Platform.fromBlockchainType(record.blockchainType)
                subtitle = recordType.type
                primaryValue = null
                secondaryValue = null
            }
        }

        return TransactionViewItem(
            uid = record.uid,
            progress = null,
            title = record.listTitle(),
            subtitle = subtitle,
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            showAmount = showAmount,
            sentToSelf = sentToSelf,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            icon = icon ?: iconX
        )
    }

    private fun createViewItemFromThorchainTransactionRecord(
        record: ThorchainTransactionRecord,
        currencyValue: CurrencyValue?,
        progress: Float?,
        icon: TransactionViewItem.Icon?
    ): TransactionViewItem {
        val subtitle: String
        val primaryValue: ColoredValue
        when (val type = record.type) {
            is ThorchainTransactionRecord.Type.Incoming -> {
                subtitle = type.from?.let {
                    Translator.getString(Res.string.Transactions_From, mapped(it, record.blockchainType))
                } ?: "---"
                primaryValue = getColoredValue(type.value, ColorName.Remus)
            }

            is ThorchainTransactionRecord.Type.Outgoing -> {
                subtitle = type.to?.let {
                    Translator.getString(Res.string.Transactions_To, mapped(it, record.blockchainType))
                } ?: "---"
                primaryValue = if (type.sentToSelf) {
                    ColoredValue(getCoinString(type.value, true), ColorName.Secondary)
                } else {
                    getColoredValue(type.value, getAmountColorForSend(icon))
                }
            }
        }

        return TransactionViewItem(
            uid = record.uid,
            progress = progress,
            title = record.listTitle(),
            subtitle = subtitle,
            primaryValue = primaryValue,
            secondaryValue = currencyValue?.let { getColoredValue(it, ColorName.Secondary) },
            showAmount = showAmount,
            sentToSelf = record.sentToSelf,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            spam = record.spam,
            icon = icon ?: singleValueIconType(record.mainValue)
        )
    }

    private fun createViewItemFromSolanaUnknownTransactionRecord(
        record: SolanaTransactionRecord,
        currencyValue: CurrencyValue?,
        progress: Float?,
        icon: TransactionViewItem.Icon.Failed?
    ): TransactionViewItem {
        val incomingValues = record.incomingSolanaTransfers?.map { it.value }.orEmpty()
        val outgoingValues = record.outgoingSolanaTransfers?.map { it.value }.orEmpty()
        val (primaryValue: ColoredValue?, secondaryValue: ColoredValue?) = getValues(
            incomingValues = incomingValues,
            outgoingValues = outgoingValues,
            currencyValue = currencyValue
        )

        return TransactionViewItem(
            uid = record.uid,
            progress = progress,
            title = record.listTitle(),
            subtitle = Translator.getString(Res.string.Transactions_Unknown_Description),
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            showAmount = showAmount,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            spam = record.spam,
            icon = icon ?: iconType(
                record.blockchainType,
                incomingValues,
                outgoingValues
            )
        )
    }

    private fun createViewItemFromSolanaOutgoingTransactionRecord(
        record: SolanaTransactionRecord,
        currencyValue: CurrencyValue?,
        progress: Float?,
        icon: TransactionViewItem.Icon.Failed?
    ): TransactionViewItem {
        val primaryValue = if (record.sentToSelf) {
            ColoredValue(getCoinString(record.mainValue!!, true), ColorName.Primary)
        } else {
            getColoredValue(record.mainValue!!, ColorName.Lucian)
        }
        val secondaryValue =
            singleValueSecondaryValue(record.mainValue!!, currencyValue)

        return TransactionViewItem(
            uid = record.uid,
            progress = progress,
            title = record.listTitle(),
            subtitle = record.to?.let { to ->
                Translator.getString(
                    Res.string.Transactions_To,
                    mapped(to.first(), record.blockchainType)
                )
            } ?: "",
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            showAmount = showAmount,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            sentToSelf = record.sentToSelf,
            spam = record.spam,
            icon = icon ?: singleValueIconType(record.mainValue!!)
        )
    }

    private fun createViewItemFromSolanaTransactionRecord(
        record: SolanaTransactionRecord,
        transactionItem: TransactionItem,
        progress: Float?,
        icon: TransactionViewItem.Icon.Failed?,
    ): TransactionViewItem {
        return when (record.transactionRecordType) {
            TransactionRecordType.SOLANA_INCOMING ->
                createViewItemFromSolanaIncomingTransactionRecord(
                    record = record,
                    currencyValue = transactionItem.currencyValue,
                    progress = progress,
                    icon = icon
                )

            TransactionRecordType.SOLANA_OUTGOING ->
                createViewItemFromSolanaOutgoingTransactionRecord(
                    record = record,
                    currencyValue = transactionItem.currencyValue,
                    progress = progress,
                    icon = icon
                )

            TransactionRecordType.SOLANA_UNKNOWN ->
                createViewItemFromSolanaUnknownTransactionRecord(
                    record = record,
                    currencyValue = transactionItem.currencyValue,
                    progress = progress,
                    icon = icon
                )

            else -> throw IllegalArgumentException("Undefined record type ${record.javaClass.name}")
        }
    }

    private fun createViewItemFromSolanaIncomingTransactionRecord(
        record: SolanaTransactionRecord,
        currencyValue: CurrencyValue?,
        progress: Float?,
        icon: TransactionViewItem.Icon.Failed?
    ): TransactionViewItem {
        val primaryValue = getColoredValue(record.mainValue!!, ColorName.Remus)
        val secondaryValue =
            singleValueSecondaryValue(record.mainValue!!, currencyValue)

        return TransactionViewItem(
            uid = record.uid,
            progress = progress,
            title = record.listTitle(),
            subtitle = record.from?.let { from ->
                Translator.getString(
                    Res.string.Transactions_From,
                    mapped(from, record.blockchainType)
                )
            } ?: "",
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            showAmount = showAmount,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            spam = record.spam,
            icon = icon ?: singleValueIconType(record.mainValue!!)
        )
    }

    private fun mapped(address: String, blockchainType: BlockchainType): String {
        return addressMetadataManager.mapped(blockchainType, address)
    }

    private fun createViewItemFromSwapTransactionRecord(
        record: EvmTransactionRecord,
        progress: Float?,
        icon: TransactionViewItem.Icon?
    ): TransactionViewItem {
        val primaryValue = record.valueOut?.let {
            getColoredValue(it, amountColor(incoming = true, sentToThirdParty = record.recipient != null))
        }
        val secondaryValue = getColoredValue(record.valueIn!!, amountColor(incoming = false))

        return TransactionViewItem(
            uid = record.uid,
            progress = progress,
            title = record.listTitle(),
            subtitle = mapped(record.exchangeAddress!!, record.blockchainType),
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            showAmount = showAmount,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            spam = record.spam,
            icon = icon ?: doubleValueIconType(record.valueOut, record.valueIn)
        )
    }

    private fun createViewItemFromUnknownSwapTransactionRecord(
        record: EvmTransactionRecord,
        progress: Float?,
        icon: TransactionViewItem.Icon?
    ): TransactionViewItem {
        val primaryValue = record.valueOut?.let { getColoredValue(it, ColorName.Remus) }
        val secondaryValue = record.valueIn?.let { getColoredValue(it, ColorName.Lucian) }

        return TransactionViewItem(
            uid = record.uid,
            progress = progress,
            title = record.listTitle(),
            subtitle = mapped(record.exchangeAddress!!, record.blockchainType),
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            showAmount = showAmount,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            spam = record.spam,
            icon = icon ?: doubleValueIconType(record.valueOut, record.valueIn)
        )
    }

    private fun createViewItemFromSimpleTronTransactionRecord(
        uid: String,
        title: String,
        timestamp: Long,
        progress: Float?,
        spam: Boolean,
        icon: TransactionViewItem.Icon?
    ): TransactionViewItem {
        return TransactionViewItem(
            uid = uid,
            progress = progress,
            title = title,
            subtitle = Translator.getString(Res.string.Transactions_Unknown_Description),
            primaryValue = null,
            secondaryValue = null,
            date = Date(timestamp * 1000),
            formattedTime = formatTime(timestamp),
            spam = spam,
            icon = icon ?: TransactionViewItem.Icon.Platform.fromBlockchainType(BlockchainType.Tron)
        )
    }

    private fun createViewItemFromEvmTransactionRecord(
        uid: String,
        title: String,
        timestamp: Long,
        blockchainType: BlockchainType,
        progress: Float?,
        spam: Boolean,
        icon: TransactionViewItem.Icon?
    ): TransactionViewItem {
        return TransactionViewItem(
            uid = uid,
            progress = progress,
            title = title,
            subtitle = Translator.getString(Res.string.Transactions_Unknown_Description),
            primaryValue = null,
            secondaryValue = null,
            date = Date(timestamp * 1000),
            formattedTime = formatTime(timestamp),
            spam = spam,
            icon = icon ?: TransactionViewItem.Icon.Platform.fromBlockchainType(blockchainType)
        )
    }

    private fun createViewItemFromEvmOutgoingTransactionRecord(
        transactionItem: TransactionItem,
        value: TransactionValue,
        to: String,
        sentToSelf: Boolean,
        progress: Float?,
        icon: TransactionViewItem.Icon?
    ): TransactionViewItem {
        val record = transactionItem.record
        val primaryValue = if (sentToSelf) {
            ColoredValue(getCoinString(value, true), ColorName.Primary)
        } else {
            getColoredValue(value, ColorName.Lucian)
        }

        val secondaryValue = singleValueSecondaryValue(value, transactionItem.currencyValue)

        return TransactionViewItem(
            uid = record.uid,
            progress = progress,
            title = record.listTitle(),
            subtitle = Translator.getString(Res.string.Transactions_To, mapped(to, record.blockchainType)),
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            showAmount = showAmount,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            sentToSelf = sentToSelf,
            spam = record.spam,
            icon = icon ?: singleValueIconType(value)
        )
    }

    private fun getAmountColorForSend(icon: TransactionViewItem.Icon?): ColorName {
        return when (icon) {
            is TransactionViewItem.Icon.Failed -> ColorName.Secondary
            else -> ColorName.Primary
        }
    }

    private fun tonTransferColoredValue(value: TransactionValue, color: ColorName): ColoredValue =
        if (value is TransactionValue.NftValue) ColoredValue(value.coinCode, color) else getColoredValue(value, color)

    private fun getColoredValue(
        value: Any,
        color: ColorName,
        hideSign: Boolean = false
    ): ColoredValue =
        when (value) {
            is TransactionValue -> ColoredValue(
                value = getCoinString(value, hideSign),
                color = if (value.zeroValue) ColorName.Primary else color
            )

            is CurrencyValue -> ColoredValue(
                getCurrencyString(value),
                if (value.value.compareTo(BigDecimal.ZERO) == 0) ColorName.Secondary else color
            )

            else -> ColoredValue(value.toString(), color)
        }

    private fun createViewItemFromEvmIncomingTransactionRecord(
        transactionItem: TransactionItem,
        value: TransactionValue,
        from: String,
        progress: Float?,
        icon: TransactionViewItem.Icon?
    ): TransactionViewItem {
        val record = transactionItem.record
        val primaryValue = getColoredValue(value, ColorName.Remus)
        val secondaryValue = transactionItem.currencyValue?.let {
            getColoredValue(it, ColorName.Secondary)
        }

        return TransactionViewItem(
            uid = record.uid,
            progress = progress,
            title = record.listTitle(),
            subtitle = Translator.getString(
                Res.string.Transactions_From,
                mapped(from, record.blockchainType)
            ),
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            showAmount = showAmount,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            spam = record.spam,
            icon = icon ?: singleValueIconType(value)
        )
    }

    private fun createViewItemFromContractCreationTransactionRecord(
        record: EvmTransactionRecord,
        progress: Float?,
        icon: TransactionViewItem.Icon?
    ): TransactionViewItem {
        return TransactionViewItem(
            uid = record.uid,
            progress = progress,
            title = record.listTitle(),
            subtitle = "---",
            primaryValue = null,
            secondaryValue = null,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            spam = record.spam,
            icon = icon ?: TransactionViewItem.Icon.Platform.fromBlockchainType(record.blockchainType)
        )
    }

    private fun createViewItemFromDelegationTransactionRecord(
        record: EvmTransactionRecord,
        progress: Float?,
        icon: TransactionViewItem.Icon?
    ): TransactionViewItem {
        val delegatedTo = checkNotNull(record.delegatedTo)

        val subtitle: String
        var statusValue: ColoredValue? = null
        if (record.delegationRevoked) {
            subtitle = "---"
        } else {
            subtitle = Translator.getString(
                Res.string.transactions_account_delegated_to,
                mapped(delegatedTo, record.blockchainType)
            )
            if (record.delegationActive) {
                statusValue = ColoredValue(
                    Translator.getString(Res.string.transactions_account_delegation_warning_short),
                    ColorName.Warning
                )
            }
        }

        return TransactionViewItem(
            uid = record.uid,
            progress = progress,
            title = record.listTitle(),
            subtitle = subtitle,
            primaryValue = null,
            secondaryValue = null,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            spam = record.spam,
            icon = icon ?: TransactionViewItem.Icon.Platform.fromBlockchainType(record.blockchainType),
            statusValue = statusValue,
        )
    }

    private fun createViewItemFromContractCallTransactionRecord(
        transactionItem: TransactionItem,
        incomingValues: List<TransactionValue>,
        outgoingValues: List<TransactionValue>,
        contractAddress: String,
        progress: Float?,
        icon: TransactionViewItem.Icon?
    ): TransactionViewItem {
        val record = transactionItem.record
        val (primaryValue: ColoredValue?, secondaryValue: ColoredValue?) = getValues(
            incomingValues,
            outgoingValues,
            transactionItem.currencyValue
        )
        return TransactionViewItem(
            uid = record.uid,
            progress = progress,
            title = record.listTitle(),
            subtitle = mapped(contractAddress, record.blockchainType),
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            showAmount = showAmount,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            spam = record.spam,
            icon = icon ?: iconType(record.blockchainType, incomingValues, outgoingValues)
        )
    }

    private fun counterpartySubtitle(
        events: List<TransferEvent>,
        format: StringResource,
        blockchainType: BlockchainType
    ): String {
        val addresses = events.mapNotNull { it.address }.toSet()
        return if (addresses.size == 1) {
            Translator.getString(format, mapped(addresses.first(), blockchainType))
        } else {
            Translator.getString(Res.string.Transactions_Multiple)
        }
    }

    private fun createViewItemFromExternalContractCallTransactionRecord(
        transactionItem: TransactionItem,
        incomingValues: List<TransactionValue>,
        outgoingValues: List<TransactionValue>,
        incomingEvents: List<TransferEvent>,
        outgoingEvents: List<TransferEvent>,
        progress: Float?,
        icon: TransactionViewItem.Icon?
    ): TransactionViewItem {
        val record = transactionItem.record
        val blockchainType = record.blockchainType

        val (primaryValue: ColoredValue?, secondaryValue: ColoredValue?) = getValues(
            incomingValues,
            outgoingValues,
            transactionItem.currencyValue
        )

        val subTitle = when {
            outgoingValues.isEmpty() ->
                counterpartySubtitle(incomingEvents, Res.string.Transactions_From, blockchainType)

            incomingValues.isEmpty() ->
                counterpartySubtitle(outgoingEvents, Res.string.Transactions_To, blockchainType)

            else -> "---"
        }

        return TransactionViewItem(
            uid = record.uid,
            progress = progress,
            title = record.listTitle(),
            subtitle = subTitle,
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            showAmount = showAmount,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            spam = record.spam,
            icon = icon ?: iconType(blockchainType, incomingValues, outgoingValues)
        )
    }

    private fun createViewItemFromBitcoinTransactionRecord(
        record: BitcoinTransactionRecord,
        currencyValue: CurrencyValue?,
        progress: Float?,
        lastBlockTimestamp: Long?,
        icon: TransactionViewItem.Icon?,
    ): TransactionViewItem {
        return if (record.transactionRecordType == TransactionRecordType.BITCOIN_INCOMING) {
            createViewItemFromBitcoinIncomingTransactionRecord(
                record = record,
                currencyValue = currencyValue,
                progress = progress,
                lastBlockTimestamp = lastBlockTimestamp,
                icon = icon
            )
        } else {
            createViewItemFromBitcoinOutgoingTransactionRecord(
                record = record,
                currencyValue = currencyValue,
                progress = progress,
                lastBlockTimestamp = lastBlockTimestamp,
                icon = icon
            )
        }
    }

    // The swap may still sit on another member of the chain this visible record won.
    private suspend fun relinkReplacementChainSwap(record: BitcoinTransactionRecord): SwapProviderTransaction? {
        val chain = swapProviderRecordResolver.replacementChainOf(record) ?: return null
        return relinkSwapToReplacement.relink(
            candidateUids = record.replacementCandidateUids,
            chainHashes = chain.hashes,
            toUid = record.uid,
            toHash = record.transactionHash,
        )
    }

    private fun createViewItemFromBitcoinOutgoingTransactionRecord(
        record: BitcoinTransactionRecord,
        currencyValue: CurrencyValue?,
        progress: Float?,
        lastBlockTimestamp: Long?,
        icon: TransactionViewItem.Icon?
    ): TransactionViewItem {
        val recipient = record.to?.firstOrNull()
        val subtitle = when {
            record.isIronwoodMigration ->
                Translator.getString(Res.string.transactions_migrate_to_ironwood)

            recipient != null -> Translator.getString(
                Res.string.Transactions_To,
                mapped(recipient, record.blockchainType)
            )

            else -> "---"
        }

        val primaryValue = if (record.sentToSelf || record.isIronwoodMigration) {
            ColoredValue(getCoinString(record.mainValue, true), ColorName.Primary)
        } else {
            getColoredValue(record.mainValue, ColorName.Lucian)
        }

        val secondaryValue = currencyValue?.let {
            getColoredValue(it, ColorName.Secondary)
        }

        val lockState = record.lockState(lastBlockTimestamp)
        val locked = when {
            lockState == null -> null
            lockState.locked -> true
            else -> false
        }

        return TransactionViewItem(
            uid = record.uid,
            progress = progress,
            title = record.listTitle(),
            subtitle = subtitle,
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            showAmount = showAmount,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            sentToSelf = record.sentToSelf,
            doubleSpend = record.conflictingHash != null,
            locked = locked,
            spam = record.spam,
            icon = icon ?: if (record.isIronwoodMigration) {
                TransactionViewItem.Icon.ImageResource(Res.drawable.ic_migrate_24)
            } else {
                singleValueIconType(record.mainValue)
            }
        )
    }

    private fun createViewItemFromBitcoinIncomingTransactionRecord(
        record: BitcoinTransactionRecord,
        currencyValue: CurrencyValue?,
        progress: Float?,
        lastBlockTimestamp: Long?,
        icon: TransactionViewItem.Icon?
    ): TransactionViewItem {
        val subtitle = record.from?.let {
            Translator.getString(
                Res.string.Transactions_From,
                mapped(it, record.blockchainType)
            )
        } ?: "---"

        val primaryValue = getColoredValue(record.mainValue, ColorName.Remus)
        val secondaryValue = currencyValue?.let {
            getColoredValue(it, ColorName.Secondary)
        }

        val lockState = record.lockState(lastBlockTimestamp)
        val locked = when {
            lockState == null -> null
            lockState.locked -> true
            else -> false
        }

        return TransactionViewItem(
            uid = record.uid,
            progress = progress,
            title = record.listTitle(),
            subtitle = subtitle,
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            showAmount = showAmount,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            sentToSelf = false,
            doubleSpend = record.conflictingHash != null,
            locked = locked,
            spam = record.spam,
            icon = icon ?: singleValueIconType(record.mainValue)
        )
    }

    private fun userSwapProviderViewItem(
        transactionItem: TransactionItem,
        matchedSwap: SwapProviderTransaction?,
        progress: Float?,
    ): TransactionViewItem? {
        val record = transactionItem.record
        val resolved = swapProviderRecordResolver.findAndLink(record, matchedSwap) ?: return null
        return createViewItemFromUserSwapProviderRecord(
            transaction = resolved.swap,
            recordUid = record.uid,
            timestamp = record.timestamp,
            direct = !resolved.isIncoming,
            onChainProgress = progress.takeIf { resolved.isIncoming },
        )
    }

    private fun syncReceivedAt(swap: SwapProviderTransaction, recordUid: String, timestampSeconds: Long) {
        val receivedAt = timestampSeconds * 1000
        if (swap.receivedAt != receivedAt) {
            swapProviderTransactionsStorage.syncReceivedAt(swap.date, recordUid, receivedAt)
        }
    }

    private fun createViewItemFromUserSwapProviderRecord(
        transaction: SwapProviderTransaction,
        recordUid: String,
        timestamp: Long,
        direct: Boolean,
        onChainProgress: Float?,
    ): TransactionViewItem {
        if (!direct) syncReceivedAt(transaction, recordUid, timestamp)
        val iconIn = getIconForToken(
            coinUid = transaction.coinUidIn
        )
        val iconOut = getIconForToken(
            coinUid = transaction.coinUidOut
        )

        val transactionIcon = TransactionViewItem.Icon.Double(
            back = iconIn,
            front = iconOut
        )

        val valueInFormatted = getFormattedAmount(
            coinUid = transaction.coinUidIn,
            amount = transaction.amountIn,
            negative = true
        )

        val valueOutFormatted = getFormattedAmount(
            coinUid = transaction.coinUidOut,
            amount = transaction.amountOut,
            negative = false
        )

        val primaryValue = if (direct) {
            ColoredValue(valueInFormatted, ColorName.Lucian)
        } else {
            ColoredValue(valueOutFormatted, ColorName.Remus)  // Received = green
        }
        val secondaryValue = if (direct) {
            ColoredValue(valueOutFormatted, ColorName.Remus)
        } else {
            ColoredValue(valueInFormatted, ColorName.Lucian)  // Sent = red
        }
        val providerStatus = transaction.status.toStatus()
        val payoutConfirmationProgress = onChainProgress.takeIf {
            !direct && providerStatus == TransactionStatusEnum.FINISHED
        }
        val transactionStatusUrl = transaction.toStatusUrl()

        return TransactionViewItem(
            uid = recordUid,
            progress = payoutConfirmationProgress ?: if (transaction.isFinished()) {
                0f
            } else {
                (providerStatus.ordinal + 1) * (1f / (TransactionStatusEnum.FINISHED.ordinal + 1))
            },
            title = transaction.displayStatus(payoutConfirmationProgress != null).listTitle(),
            subtitle = unstoppableProviderTitles.swapProviderDisplayTitle(
                transaction.provider,
                transaction.subProviderId,
            ),
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            showAmount = showAmount,
            date = Date(timestamp * 1000),
            formattedTime = formatTime(timestamp),
            spam = false,
            icon = transactionIcon,
            changeNowTransactionId = transaction.transactionId,
            transactionStatusUrl = transactionStatusUrl
        )
    }

    private fun getFormattedAmount(
        coinUid: String,
        amount: BigDecimal,
        negative: Boolean
    ): String {
        val sign = if (negative) "-" else "+"
        val coinCode = PayCoreAssetResolver.coinCode(coinUid)
            ?: marketKit.coin(coinUid)?.code
            ?: coinUid.uppercase()

        val numberFormatted = numberFormatter.formatCoinShort(
            amount,
            coinCode,
            8
        )
        return "$sign $numberFormatted"
    }

    private fun createViewItemFromTronTransactionRecord(
        transactionItem: TransactionItem,
        value: TransactionValue?,
        sentToSelf: Boolean,
        from: String?,
        to: String?,
        spender: String?,
        progress: Float?,
        contractAddress: String?,
        incomingEvents: List<TransferEvent>?,
        outgoingEvents: List<TransferEvent>?,
        icon: TransactionViewItem.Icon?,
    ): TransactionViewItem = when (transactionItem.record.transactionRecordType) {
        TransactionRecordType.TRON_APPROVE ->
            createViewItemFromApproveTransactionRecord(
                transactionItem = transactionItem,
                value = value!!,
                spender = spender!!,
                progress = progress,
                icon = icon
            )


        TransactionRecordType.TRON_CONTRACT_CALL -> {
            val (incomingValues, outgoingValues) = EvmTransactionRecord.combined(
                incomingEvents!!,
                outgoingEvents!!
            )
            createViewItemFromContractCallTransactionRecord(
                transactionItem = transactionItem,
                incomingValues = incomingValues,
                outgoingValues = outgoingValues,
                contractAddress = contractAddress!!,
                progress = progress,
                icon = icon,
            )
        }

        TransactionRecordType.TRON_EXTERNAL_CONTRACT_CALL -> {
            val (incomingValues, outgoingValues) = EvmTransactionRecord.combined(
                incomingEvents!!,
                outgoingEvents!!
            )
            createViewItemFromExternalContractCallTransactionRecord(
                transactionItem = transactionItem,
                incomingValues = incomingValues,
                outgoingValues = outgoingValues,
                incomingEvents = incomingEvents,
                outgoingEvents = outgoingEvents,
                progress = progress,
                icon = icon
            )
        }

        TransactionRecordType.TRON_INCOMING ->
            createViewItemFromEvmIncomingTransactionRecord(
                transactionItem = transactionItem,
                value = value!!,
                from = from!!,
                progress = progress,
                icon = icon
            )

        TransactionRecordType.TRON_OUTGOING ->
            createViewItemFromEvmOutgoingTransactionRecord(
                transactionItem = transactionItem,
                value = value!!,
                to = to!!,
                sentToSelf = sentToSelf,
                progress = progress,
                icon = icon,
            )

        TransactionRecordType.TRON -> {
            createViewItemFromSimpleTronTransactionRecord(
                uid = transactionItem.record.uid,
                title = transactionItem.record.listTitle(),
                timestamp = transactionItem.record.timestamp,
                progress = progress,
                spam = transactionItem.record.spam,
                icon = icon
            )
        }

        else -> throw IllegalArgumentException("Undefined record type")
    }

    private fun createViewItemFromApproveTransactionRecord(
        transactionItem: TransactionItem,
        value: TransactionValue,
        spender: String,
        progress: Float?,
        icon: TransactionViewItem.Icon?
    ): TransactionViewItem {
        val record = transactionItem.record
        val primaryValueText: String
        val secondaryValueText: String?

        if (value.isMaxValue) {
            primaryValueText = "∞"
            secondaryValueText = if (value.coinCode.isEmpty()) "" else Translator.getString(
                Res.string.Transaction_Unlimited_CoinCode,
                value.coinCode
            )
        } else {
            primaryValueText = getCoinString(value, hideSign = true)
            secondaryValueText = transactionItem.currencyValue?.let { getCurrencyString(it) }
        }

        val primaryValue = ColoredValue(primaryValueText, ColorName.Primary)
        val secondaryValue = secondaryValueText?.let { ColoredValue(it, ColorName.Secondary) }

        return TransactionViewItem(
            uid = record.uid,
            progress = progress,
            title = record.listTitle(),
            subtitle = mapped(spender, record.blockchainType),
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            showAmount = showAmount,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            spam = record.spam,
            icon = icon ?: singleValueIconType(value)
        )
    }

    private fun singleValueSecondaryValue(
        value: TransactionValue,
        currencyValue: CurrencyValue?
    ): ColoredValue? =
        when (value) {
            is TransactionValue.NftValue -> {
                val text = value.tokenName?.let { "$it #${value.mintAddress}" }
                    ?: "#${value.mintAddress}"
                getColoredValue(text, ColorName.Secondary)
            }

            is TransactionValue.CoinValue,
            is TransactionValue.RawValue,
            is TransactionValue.JettonValue,
            is TransactionValue.TokenValue -> {
                currencyValue?.let { getColoredValue(it, ColorName.Secondary) }
            }
        }

    private fun getLiquidityValues(
        incomingValues: List<TransactionValue>,
        outgoingValues: List<TransactionValue>,
        currencyValue: CurrencyValue?
    ): Pair<ColoredValue?, ColoredValue?> {
        val (values, color) = when {
            incomingValues.size == 2 && outgoingValues.isEmpty() -> incomingValues to ColorName.Remus
            outgoingValues.size == 2 && incomingValues.isEmpty() -> outgoingValues to ColorName.Lucian
            else -> return getValues(incomingValues, outgoingValues, currencyValue)
        }
        return getColoredValue(values[0], color) to getColoredValue(values[1], color)
    }

    private fun getValues(
        incomingValues: List<TransactionValue>,
        outgoingValues: List<TransactionValue>,
        currencyValue: CurrencyValue?
    ): Pair<ColoredValue, ColoredValue?> {
        val primaryValue: ColoredValue?
        val secondaryValue: ColoredValue?

        when {
            // incoming
            (incomingValues.size == 1 && outgoingValues.isEmpty()) -> {
                val transactionValue = incomingValues.first()
                primaryValue = getColoredValue(transactionValue, ColorName.Remus)
                secondaryValue =
                    singleValueSecondaryValue(transactionValue, currencyValue)
            }

            // outgoing
            (incomingValues.isEmpty() && outgoingValues.size == 1) -> {
                val transactionValue = outgoingValues.first()
                primaryValue = getColoredValue(transactionValue, ColorName.Lucian)
                secondaryValue =
                    singleValueSecondaryValue(transactionValue, currencyValue)
            }

            // swap
            (incomingValues.size == 1 && outgoingValues.size == 1) -> {
                val inTransactionValue = incomingValues.first()
                val outTransactionValue = outgoingValues.first()
                primaryValue = getColoredValue(inTransactionValue, ColorName.Remus)
                secondaryValue = getColoredValue(outTransactionValue, ColorName.Lucian)
            }

            // outgoing multiple
            (incomingValues.isEmpty() && outgoingValues.isNotEmpty()) -> {
                primaryValue = getColoredValue(
                    outgoingValues.map { it.coinCode }.toSet().toList()
                        .joinToString(", "),
                    ColorName.Lucian
                )
                secondaryValue = getColoredValue(
                    Translator.getString(Res.string.Transactions_Multiple),
                    ColorName.Secondary
                )
            }

            // incoming multiple
            (incomingValues.isNotEmpty() && outgoingValues.isEmpty()) -> {
                primaryValue = getColoredValue(
                    incomingValues.map { it.coinCode }.toSet().toList()
                        .joinToString(", "),
                    ColorName.Remus
                )
                secondaryValue = getColoredValue(
                    Translator.getString(Res.string.Transactions_Multiple),
                    ColorName.Secondary
                )
            }

            else -> {
                primaryValue = if (incomingValues.size == 1) {
                    getColoredValue(incomingValues.first(), ColorName.Remus)
                } else {
                    getColoredValue(
                        incomingValues.joinToString(", ") { it.coinCode },
                        ColorName.Remus
                    )
                }
                secondaryValue = if (outgoingValues.size == 1) {
                    getColoredValue(outgoingValues.first(), ColorName.Remus)
                } else {
                    getColoredValue(
                        outgoingValues.map { it.coinCode }.toSet().toList().joinToString(", "),
                        ColorName.Lucian
                    )
                }
            }
        }
        return Pair(primaryValue, secondaryValue)
    }

    private fun getCurrencyString(currencyValue: CurrencyValue): String {
        return numberFormatter.formatFiatShort(
            currencyValue.value.abs(),
            currencyValue.currency.symbol,
            2
        )
    }

    private fun getCoinString(
        transactionValue: TransactionValue,
        hideSign: Boolean = false
    ): String {
        return transactionValue.decimalValue?.let { decimalValue ->
            val sign = when {
                hideSign -> ""
                decimalValue < BigDecimal.ZERO -> "-"
                decimalValue > BigDecimal.ZERO -> "+"
                else -> ""
            }
            sign + numberFormatter.formatCoinShort(
                decimalValue.abs(),
                transactionValue.coinCode,
                transactionValue.decimals ?: 8,
            )
        } ?: ""
    }

    private fun formatTime(timestamp: Long): String = DateHelper.getOnlyTime(Date(timestamp * 1000))

    private fun createViewItemFromPendingTransactionRecord(
        record: PendingTransactionRecord,
        icon: TransactionViewItem.Icon?,
        currencyValue: CurrencyValue?,
    ): TransactionViewItem {
        val isExpired = record.isExpired
        val subtitle = if (record.isIronwoodMigration) {
            Translator.getString(Res.string.transactions_migrate_to_ironwood)
        } else {
            record.to?.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
                Translator.getString(Res.string.Transactions_To, mapped(it, record.blockchainType))
            } ?: "---"
        }

        val secondaryValue = currencyValue?.let {
            getColoredValue(it, ColorName.Secondary)
        }
        val amountColor = when {
            isExpired -> ColorName.Secondary
            record.isIronwoodMigration -> ColorName.Primary
            else -> ColorName.Lucian
        }
        return TransactionViewItem(
            uid = record.uid,
            progress = 0.15f,
            title = record.listTitle(),
            subtitle = subtitle,
            primaryValue = getColoredValue(
                record.mainValue,
                amountColor,
                hideSign = record.isIronwoodMigration
            ),
            secondaryValue = secondaryValue,
            date = Date(record.timestamp * 1000),
            formattedTime = formatTime(record.timestamp),
            sentToSelf = record.sentToSelf,
            doubleSpend = false,
            locked = null,
            icon = icon ?: if (record.isIronwoodMigration) {
                TransactionViewItem.Icon.ImageResource(Res.drawable.ic_migrate_24)
            } else {
                getIconForToken(record.token.coin.uid)
            },
            spam = false,
            showAmount = showAmount
        )
    }
}
