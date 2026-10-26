package cash.p.terminal.modules.balance

import androidx.compose.runtime.Immutable
import cash.p.terminal.core.NotSupportedOnPlatformException
import cash.p.terminal.core.diffPercentage
import cash.p.terminal.core.isBlockedByProvider
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.isNetworkPaused
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.ledger.domain.LedgerNotFoundException
import cash.p.terminal.ledger.ui.ledgerErrorMessageOrNull
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.modules.balance.BalanceModule.warningText
import cash.p.terminal.modules.displayoptions.DisplayDiffOptionType
import cash.p.terminal.modules.offline.OfflineOperationGate.Companion.availability
import cash.p.terminal.modules.offline.OperationAvailability
import cash.p.terminal.resources.Balance_FoundTx
import cash.p.terminal.resources.Balance_LockedAmount_Title
import cash.p.terminal.resources.Balance_NotRelayedAmount_Title
import cash.p.terminal.resources.Balance_ProcessingBalance_Title
import cash.p.terminal.resources.Balance_Reserved_Title
import cash.p.terminal.resources.Balance_SearchingTransactions
import cash.p.terminal.resources.Balance_SyncedUntil
import cash.p.terminal.resources.Balance_Syncing
import cash.p.terminal.resources.Balance_Syncing_WithProgress
import cash.p.terminal.resources.CoinPage_MarketCap_Million
import cash.p.terminal.resources.CoinPage_MarketCap_Thousand
import cash.p.terminal.resources.Hud_Text_NoInternet
import cash.p.terminal.resources.Info_LockTime_Title
import cash.p.terminal.resources.Info_NotRelayed_Description
import cash.p.terminal.resources.Info_NotRelayed_Title
import cash.p.terminal.resources.Info_ProcessingBalance_Description
import cash.p.terminal.resources.Info_ProcessingBalance_Title
import cash.p.terminal.resources.Info_Reserved_CurrentlyLocked
import cash.p.terminal.resources.Info_Reserved_Description
import cash.p.terminal.resources.Info_Reserved_Title
import cash.p.terminal.resources.Info_Reserved_WalletAction
import cash.p.terminal.resources.LargeNumberFormat
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.balance_connecting
import cash.p.terminal.resources.balance_connecting_to_api
import cash.p.terminal.resources.balance_processing
import cash.p.terminal.resources.balance_syncing_blocks_remaining
import cash.p.terminal.resources.balance_waiting_for_peers
import cash.p.terminal.resources.beam_locked_balance_info
import cash.p.terminal.resources.beam_restore_catching_up
import cash.p.terminal.resources.beam_restore_catching_up_progress
import cash.p.terminal.resources.beam_restore_counting_shielded_outputs
import cash.p.terminal.resources.beam_restore_downloading_snapshot
import cash.p.terminal.resources.beam_restore_downloading_snapshot_downloaded
import cash.p.terminal.resources.beam_restore_downloading_snapshot_progress
import cash.p.terminal.resources.beam_restore_importing_snapshot
import cash.p.terminal.resources.beam_restore_resolving_birthday
import cash.p.terminal.resources.beam_restore_scanning_wallet_outputs
import cash.p.terminal.resources.beam_restore_validating_snapshot
import cash.p.terminal.resources.ledger_monero_confirm_view_key
import cash.p.terminal.resources.ledger_waiting_for_device
import cash.p.terminal.resources.hardware_connect_device_to_sync
import cash.p.terminal.resources.balance_sync_error_fixed_source
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.diffSign
import cash.p.terminal.ui.compose.components.diffText
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.balance.BalanceItem
import cash.p.terminal.wallet.balance.BalanceViewHelper
import cash.p.terminal.wallet.balance.BalanceViewHelper.coinValue
import cash.p.terminal.wallet.balance.BalanceViewType
import cash.p.terminal.wallet.balance.DeemedValue
import cash.p.terminal.wallet.policy.PlatformOperation
import cash.p.terminal.wallet.policy.PlatformOperationSupport
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.entities.Currency
import io.horizontalsystems.core.helpers.DateHelper
import java.math.BigDecimal
import java.math.RoundingMode

@Immutable
data class BalanceViewItem(
    val wallet: Wallet,
    val primaryValue: DeemedValue<String>,
    val exchangeValue: DeemedValue<String>,
    val secondaryValue: DeemedValue<String>,
    val lockedValues: List<LockedValue>,
    val sendAvailability: OperationAvailability = OperationAvailability.Unavailable,
    val syncingProgress: SyncingProgress,
    val syncingTextValue: String?,
    val syncedUntilTextValue: String?,
    val failedIconVisible: Boolean,
    val coinIconVisible: Boolean,
    val badge: String?,
    val swapVisible: Boolean,
    val swapAvailability: OperationAvailability = OperationAvailability.Unavailable,
    val errorMessage: String?,
    val isWatchAccount: Boolean,
    val warning: WarningText?,
    val diff: BigDecimal? = null,
    val fullDiff: String = "",
    val displayDiffOptionType: DisplayDiffOptionType = DisplayDiffOptionType.BOTH,
    val offline: Boolean = false,
    val syncErrorBlockedByProvider: Boolean = false,
    val syncErrorFromDevice: Boolean = false,
    val notSupportedOnPlatform: Boolean = false,
)

data class WarningText(
    val title: TranslatableString,
    val text: TranslatableString
)

data class LockedValue(
    val title: TranslatableString,
    val infoTitle: TranslatableString,
    val info: TranslatableString,
    val coinValue: DeemedValue<String>
)

@Immutable
data class BalanceViewItem2(
    val wallet: Wallet,
    val primaryValue: DeemedValue<String>,
    val exchangeValue: DeemedValue<String>,
    val diff: BigDecimal?,
    val fullDiff: String,
    val secondaryValue: DeemedValue<String>,
    val sendAvailability: OperationAvailability = OperationAvailability.Unavailable,
    val syncingProgress: SyncingProgress,
    val syncingTextValue: String?,
    val syncedUntilTextValue: String?,
    val failedIconVisible: Boolean,
    val badge: String?,
    val swapAvailability: OperationAvailability = OperationAvailability.Unavailable,
    val errorMessage: String?,
    val isWatchAccount: Boolean,
    val isSwipeToDeleteEnabled: Boolean,
    val displayDiffOptionType: DisplayDiffOptionType,
    val stackingUnpaid: DeemedValue<String>?,
    val offline: Boolean = false,
    val syncErrorBlockedByProvider: Boolean = false,
    val syncErrorFromDevice: Boolean = false,
    val notSupportedOnPlatform: Boolean = false,
)

enum class SyncingProgressType {
    Spinner, ProgressWithRing
}

data class SyncingProgress(val type: SyncingProgressType?, val progress: Double?)

fun formatProgressPercent(progress: Double): String {
    return when {
        progress >= 10.0 -> progress.toInt().toString()
        progress >= 1.0 -> "%.1f".format(progress)
        progress >= 0.01 -> "%.2f".format(progress)
        progress > 0.0 -> "<0.01"
        else -> "0"
    }
}

private val BalanceItem.displaySyncState: AdapterState
    get() {
        val txDisplayState = when (transactionsSyncState) {
            is AdapterState.Syncing,
            is AdapterState.SearchingTxs,
            is AdapterState.NotSynced -> transactionsSyncState
            else -> null
        }
        return if (state is AdapterState.Synced && txDisplayState != null) txDisplayState else state
    }

internal val BalanceItem.notSupportedOnPlatform: Boolean
    get() = (state as? AdapterState.NotSynced)?.error is NotSupportedOnPlatformException

class BalanceViewItemFactory(
    private val offlineModeManager: OfflineModeManager,
    private val platformOperationSupport: PlatformOperationSupport,
    private val numberFormatter: IAppNumberFormatter,
) {

    private data class SyncDisplayState(
        val syncingProgress: SyncingProgress,
        val syncingTextValue: String?,
        val syncedUntilTextValue: String?,
        val failedIconVisible: Boolean,
        val coinIconVisible: Boolean,
        val offline: Boolean,
    )

    private fun syncDisplayState(
        wallet: Wallet,
        displaySyncState: AdapterState,
    ): SyncDisplayState {
        val offline = offlineModeManager.isNetworkPaused(wallet.account.id, wallet.token.blockchainType)
        return if (offline) {
            SyncDisplayState(
                syncingProgress = SyncingProgress(null, null),
                syncingTextValue = null,
                syncedUntilTextValue = null,
                failedIconVisible = false,
                coinIconVisible = true,
                offline = true,
            )
        } else {
            SyncDisplayState(
                syncingProgress = getSyncingProgress(displaySyncState, wallet.token.blockchainType),
                syncingTextValue = getSyncingText(displaySyncState),
                syncedUntilTextValue = getSyncedUntilText(displaySyncState),
                failedIconVisible = displaySyncState is AdapterState.NotSynced,
                coinIconVisible = displaySyncState !is AdapterState.NotSynced,
                offline = false,
            )
        }
    }

    private fun getSyncingProgress(
        state: AdapterState?,
        blockchainType: BlockchainType
    ): SyncingProgress {
        return when (state) {
            is AdapterState.Connecting -> SyncingProgress(SyncingProgressType.Spinner, 10.0)
            is AdapterState.Syncing -> {
                val restore = state.substatus as? AdapterState.Substatus.SnapshotRestore
                if (restore?.showsProgressRing == true && state.progress != null) {
                    SyncingProgress(SyncingProgressType.ProgressWithRing, state.progress)
                } else if (state.substatus != null) {
                    SyncingProgress(SyncingProgressType.Spinner, 10.0)
                } else {
                    val progress = state.progress
                    val progressValue = progress ?: getDefaultSyncingProgress(blockchainType).toDouble()
                    if (progress != null && progress > 0 && blockchainType.isSyncWithProgress()) {
                        SyncingProgress(SyncingProgressType.ProgressWithRing, progressValue)
                    } else {
                        SyncingProgress(SyncingProgressType.Spinner, progressValue)
                    }
                }
            }
            is AdapterState.SearchingTxs -> SyncingProgress(SyncingProgressType.Spinner, 10.0)
            else -> SyncingProgress(null, null)
        }
    }

    private fun BlockchainType.isSyncWithProgress() = when (this) {
        BlockchainType.Bitcoin,
        BlockchainType.BitcoinCash,
        BlockchainType.ECash,
        BlockchainType.Litecoin,
        BlockchainType.Dogecoin,
        BlockchainType.Dash,
        BlockchainType.PirateCash,
        BlockchainType.Cosanta,
        BlockchainType.Zcash,
        BlockchainType.Monero,
        BlockchainType.Beam,
        BlockchainType.BinanceSmartChain -> true
        else -> false
    }

    private fun getDefaultSyncingProgress(blockchainType: BlockchainType) = when (blockchainType) {
        BlockchainType.Bitcoin,
        BlockchainType.BitcoinCash,
        BlockchainType.ECash,
        BlockchainType.Litecoin,
        BlockchainType.Dogecoin,
        BlockchainType.Dash,
        BlockchainType.PirateCash,
        BlockchainType.Cosanta,
        BlockchainType.Monero,
        BlockchainType.Beam,
        BlockchainType.Zcash -> 10

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
        BlockchainType.ArbitrumOne,
        BlockchainType.Solana,
        BlockchainType.Tron,
        BlockchainType.Stellar,
        BlockchainType.Ton -> 50

        BlockchainType.Thorchain,
        BlockchainType.Mayachain,
        is BlockchainType.Unsupported -> 0
    }

    private fun getSyncingText(state: AdapterState?): String? {
        return when (state) {
            is AdapterState.Connecting -> Translator.getString(Res.string.balance_connecting)
            is AdapterState.Syncing -> getSyncingProgressText(state)
            is AdapterState.SearchingTxs -> {
                if (state.count == 0) {
                    Translator.getString(Res.string.balance_connecting_to_api)
                } else {
                    Translator.getString(Res.string.Balance_SearchingTransactions)
                }
            }
            else -> null
        }
    }

    private fun getSyncingProgressText(state: AdapterState.Syncing): String {
        when (val substatus = state.substatus) {
            is AdapterState.Substatus.WaitingForPeers -> return Translator.getString(
                Res.string.balance_waiting_for_peers,
                substatus.connected,
                substatus.required,
            )
            is AdapterState.Substatus.SnapshotRestore -> return getSnapshotRestoreText(
                substatus,
                state.progress,
            )
            AdapterState.Substatus.ConfirmViewKeyExport ->
                return Translator.getString(Res.string.ledger_monero_confirm_view_key)
            AdapterState.Substatus.WaitingForLedger ->
                return Translator.getString(Res.string.ledger_waiting_for_device)
            null -> Unit
        }

        val blocksRemained = state.blocksRemained
        val progress = state.progress
        return when {
            blocksRemained != null && blocksRemained > 0 -> {
                Translator.getString(
                    Res.string.balance_syncing_blocks_remaining,
                    formatBlocksRemaining(blocksRemained)
                )
            }
            blocksRemained == null && progress != null && progress >= 100 -> {
                Translator.getString(Res.string.balance_processing)
            }
            progress != null && progress > 0 -> {
                Translator.getString(
                    Res.string.Balance_Syncing_WithProgress,
                    formatProgressPercent(progress)
                )
            }
            else -> Translator.getString(Res.string.Balance_Syncing)
        }
    }

    private fun getSnapshotRestoreText(
        restore: AdapterState.Substatus.SnapshotRestore,
        progress: Double?,
    ): String = when (restore.stage) {
        AdapterState.SnapshotRestoreStage.ResolvingBirthday ->
            Translator.getString(Res.string.beam_restore_resolving_birthday)
        AdapterState.SnapshotRestoreStage.DownloadingSnapshot -> getSnapshotDownloadText(restore)
        AdapterState.SnapshotRestoreStage.ValidatingSnapshot ->
            Translator.getString(Res.string.beam_restore_validating_snapshot)
        AdapterState.SnapshotRestoreStage.CountingShieldedOutputs ->
            Translator.getString(Res.string.beam_restore_counting_shielded_outputs)
        AdapterState.SnapshotRestoreStage.ScanningWalletOutputs ->
            Translator.getString(Res.string.beam_restore_scanning_wallet_outputs)
        AdapterState.SnapshotRestoreStage.ImportingSnapshot ->
            Translator.getString(Res.string.beam_restore_importing_snapshot)
        AdapterState.SnapshotRestoreStage.CatchingUp -> progress?.let {
            Translator.getString(Res.string.beam_restore_catching_up_progress, formatProgressPercent(it))
        } ?: Translator.getString(Res.string.beam_restore_catching_up)
    }

    private fun getSnapshotDownloadText(restore: AdapterState.Substatus.SnapshotRestore): String {
        val downloaded = restore.downloadedBytes?.takeIf { it >= 0 } ?: return Translator.getString(
            Res.string.beam_restore_downloading_snapshot,
        )
        val downloadedMiB = formatMiB(downloaded)
        val total = restore.totalBytes?.takeIf { it > 0 } ?: return Translator.getString(
            Res.string.beam_restore_downloading_snapshot_downloaded,
            downloadedMiB,
        )
        return Translator.getString(
            Res.string.beam_restore_downloading_snapshot_progress,
            downloadedMiB,
            formatMiB(total),
        )
    }

    private fun formatMiB(bytes: Long) = numberFormatter.format(bytes / MEBIBYTE.toDouble(), 0, 1)

    private fun formatBlocksRemaining(blocks: Long): String {
        val (value, suffix) = when {
            blocks >= 1_000_000 -> (blocks / 1_000_000.0) to Translator.getString(Res.string.CoinPage_MarketCap_Million)
            blocks >= 1_000 -> (blocks / 1_000.0) to Translator.getString(Res.string.CoinPage_MarketCap_Thousand)
            else -> return blocks.toString()
        }
        val formattedValue = numberFormatter.format(value, 0, 1)
        return Translator.getString(Res.string.LargeNumberFormat, formattedValue, suffix)
    }

    private val AdapterState.Substatus.SnapshotRestore.showsProgressRing: Boolean
        get() = stage == AdapterState.SnapshotRestoreStage.DownloadingSnapshot ||
            stage == AdapterState.SnapshotRestoreStage.CatchingUp

    private fun getSyncedUntilText(state: AdapterState?): String? {
        if (state == null) {
            return null
        }

        val text = when (state) {
            is AdapterState.Syncing -> {
                if (state.substatus != null) {
                    null
                } else {
                    state.lastBlockDate?.let { date ->
                        Translator.getString(
                            Res.string.Balance_SyncedUntil, DateHelper.formatDate(
                                date, "MMM d, yyyy"
                            )
                        )
                    }
                }
            }

            is AdapterState.SearchingTxs -> {
                if (state.count > 0) {
                    Translator.getString(Res.string.Balance_FoundTx, state.count.toString())
                } else {
                    null
                }
            }

            else -> null
        }

        return text
    }

    private fun lockedCoinValue(
        state: AdapterState?,
        balance: BigDecimal,
        hideBalance: Boolean,
        coinDecimals: Int,
        token: Token
    ): DeemedValue<String>? {
        if (balance <= BigDecimal.ZERO) {
            return null
        }

        val visible = !hideBalance
        val deemed = state !is AdapterState.Synced

        val value = numberFormatter.formatCoinFull(balance, token.coin.code, coinDecimals)

        return DeemedValue(value, deemed, visible)
    }

    fun viewItem(
        item: BalanceItem,
        currency: Currency,
        hideBalance: Boolean,
        watchAccount: Boolean,
        balanceViewType: BalanceViewType,
        isSwappable: Boolean,
        displayDiffOptionType: DisplayDiffOptionType = DisplayDiffOptionType.BOTH,
    ): BalanceViewItem {
        val wallet = item.wallet
        val state = item.state
        val displaySyncState = item.displaySyncState
        val latestRate = item.coinPrice

        val balanceTotalVisibility = !hideBalance

        val (primaryValue, secondaryValue) = BalanceViewHelper.getPrimaryAndSecondaryValues(
            balance = item.balanceData.total,
            visible = balanceTotalVisibility,
            fullFormat = true,
            coinDecimals = wallet.decimal,
            dimmed = state !is AdapterState.Synced,
            coinPrice = latestRate,
            currency = currency,
            balanceViewType = balanceViewType
        )

        val lockedValues = buildList {
            lockedCoinValue(
                state = state,
                balance = item.balanceData.timeLocked,
                hideBalance = hideBalance,
                coinDecimals = wallet.decimal,
                token = wallet.token
            )?.let {
                // BEAM reports maturing coins here, mostly Max Privacy receipts waiting for their anonymity set.
                val beam = wallet.token.blockchainType == BlockchainType.Beam
                add(
                    LockedValue(
                        title = TranslatableString.ResString(Res.string.Balance_LockedAmount_Title),
                        infoTitle = TranslatableString.ResString(
                            if (beam) Res.string.Balance_LockedAmount_Title else Res.string.Info_LockTime_Title
                        ),
                        info = TranslatableString.ResString(
                            if (beam) {
                                Res.string.beam_locked_balance_info
                            } else {
                                Res.string.Info_ProcessingBalance_Description
                            }
                        ),
                        coinValue = it
                    )
                )
            }

            lockedCoinValue(
                state = state,
                balance = item.balanceData.pending,
                hideBalance = hideBalance,
                coinDecimals = wallet.decimal,
                token = wallet.token
            )?.let {
                add(
                    LockedValue(
                        title = TranslatableString.ResString(Res.string.Balance_ProcessingBalance_Title),
                        infoTitle = TranslatableString.ResString(Res.string.Info_ProcessingBalance_Title),
                        info = TranslatableString.ResString(Res.string.Info_ProcessingBalance_Description),
                        coinValue = it
                    )
                )
            }

            lockedCoinValue(
                state,
                item.balanceData.notRelayed,
                hideBalance,
                wallet.decimal,
                wallet.token
            )?.let {
                add(
                    LockedValue(
                        title = TranslatableString.ResString(Res.string.Balance_NotRelayedAmount_Title),
                        infoTitle = TranslatableString.ResString(Res.string.Info_NotRelayed_Title),
                        info = TranslatableString.ResString(Res.string.Info_NotRelayed_Description),
                        coinValue = it
                    )
                )
            }

            lockedCoinValue(
                state,
                item.balanceData.minimumBalance,
                hideBalance,
                wallet.decimal,
                wallet.token
            )?.let {
                var info =
                    TranslatableString.ResString(Res.string.Info_Reserved_Description).toString()
                info += "\n\n"
                info += TranslatableString.ResString(Res.string.Info_Reserved_CurrentlyLocked)
                    .toString()

                info += "\n1 XLM - " + TranslatableString.ResString(Res.string.Info_Reserved_WalletAction)
                    .toString()
                item.balanceData.stellarAssets.forEach {
                    info += "\n0.5 XLM - ${it.code}"
                }

                add(
                    LockedValue(
                        title = TranslatableString.ResString(Res.string.Balance_Reserved_Title),
                        infoTitle = TranslatableString.ResString(Res.string.Info_Reserved_Title),
                        info = TranslatableString.PlainString(info),
                        coinValue = it
                    )
                )
            }
        }

        val syncDisplayState = syncDisplayState(wallet, displaySyncState)
        val syncError = (displaySyncState as? AdapterState.NotSynced)?.error
        val deviceErrorMessage = deviceSyncErrorMessage(syncError)

        return BalanceViewItem(
            wallet = item.wallet,
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            lockedValues = lockedValues,
            exchangeValue = BalanceViewHelper.rateValue(latestRate, currency, true),
            sendAvailability = sendAvailability(item, syncDisplayState.offline),
            syncingProgress = syncDisplayState.syncingProgress,
            syncingTextValue = syncDisplayState.syncingTextValue,
            syncedUntilTextValue = syncDisplayState.syncedUntilTextValue,
            failedIconVisible = syncDisplayState.failedIconVisible,
            coinIconVisible = syncDisplayState.coinIconVisible,
            badge = wallet.badge,
            swapVisible = isSwappable,
            swapAvailability = swapAvailability(item, syncDisplayState.offline),
            errorMessage = syncErrorMessage(syncError, deviceErrorMessage),
            syncErrorBlockedByProvider = syncError?.isBlockedByProvider() == true,
            syncErrorFromDevice = deviceErrorMessage != null,
            isWatchAccount = watchAccount,
            warning = item.warning?.warningText,
            diff = item.coinPrice?.diffPercentage,
            fullDiff = getFullDiff(item, displayDiffOptionType, currency),
            displayDiffOptionType = displayDiffOptionType,
            offline = syncDisplayState.offline,
            notSupportedOnPlatform = item.notSupportedOnPlatform,
        )
    }

    // A hardware Monero wallet syncs only while its device is connected; a node failure is not the device's.
    private fun deviceSyncErrorMessage(error: Throwable?): String? = when {
        error is HardwareWalletOperationException && error.error == HardwareWalletErrorCode.ViewKeyExportDenied ->
            Translator.getString(Res.string.ledger_monero_confirm_view_key)

        error is HardwareWalletOperationException && error.error == HardwareWalletErrorCode.Network -> null

        error is HardwareWalletOperationException ||
            error is LedgerNotFoundException ||
            error is LedgerException.TransportClosed ->
            Translator.getString(Res.string.hardware_connect_device_to_sync)

        else -> error?.let(::ledgerErrorMessageOrNull)
    }

    private fun syncErrorMessage(error: Throwable?, deviceMessage: String?): String? = when {
        deviceMessage != null -> deviceMessage
        error is HardwareWalletOperationException && error.error == HardwareWalletErrorCode.Network ->
            Translator.getString(Res.string.balance_sync_error_fixed_source)

        else -> error?.message
    }

    private fun stackingUnpaidValue(
        item: BalanceItem,
        showStackingUnpaid: Boolean,
        visible: Boolean,
    ): DeemedValue<String>? {
        if (!showStackingUnpaid || item.balanceData.stackingUnpaid == BigDecimal.ZERO) return null
        return coinValue(
            balance = item.balanceData.stackingUnpaid,
            visible = visible,
            fullFormat = false,
            coinDecimals = item.wallet.decimal,
            dimmed = item.state !is AdapterState.Synced
        ).run {
            copy(value = this.value + " " + item.wallet.token.coin.code.uppercase())
        }
    }

    fun viewItem2(
        item: BalanceItem,
        currency: Currency,
        roundingAmount: Boolean,
        hideBalance: Boolean,
        watchAccount: Boolean,
        isSwipeToDeleteEnabled: Boolean,
        balanceViewType: BalanceViewType,
        networkAvailable: Boolean,
        showStackingUnpaid: Boolean,
        displayDiffOptionType: DisplayDiffOptionType
    ): BalanceViewItem2 {
        val wallet = item.wallet
        val state = item.state
        val displaySyncState = item.displaySyncState
        val latestRate = item.coinPrice

        val balanceTotalVisibility = !hideBalance

        val (primaryValue, secondaryValue) = BalanceViewHelper.getPrimaryAndSecondaryValues(
            balance = item.balanceData.total,
            visible = balanceTotalVisibility,
            fullFormat = !roundingAmount,
            coinDecimals = wallet.decimal,
            dimmed = state !is AdapterState.Synced,
            coinPrice = latestRate,
            currency = currency,
            balanceViewType = balanceViewType
        )

        val stackingUnpaid = stackingUnpaidValue(item, showStackingUnpaid, balanceTotalVisibility)

        val syncError = (displaySyncState as? AdapterState.NotSynced)?.error
        val deviceErrorMessage = deviceSyncErrorMessage(syncError)
        val errorMessage = if (networkAvailable) {
            syncErrorMessage(syncError, deviceErrorMessage)
        } else {
            Translator.getString(Res.string.Hud_Text_NoInternet)
        }

        val syncDisplayState = syncDisplayState(wallet, displaySyncState)

        return BalanceViewItem2(
            wallet = item.wallet,
            primaryValue = primaryValue,
            secondaryValue = secondaryValue,
            exchangeValue = BalanceViewHelper.rateValue(latestRate, currency, true),
            diff = item.coinPrice?.diffPercentage,
            fullDiff = getFullDiff(item, displayDiffOptionType, currency),
            sendAvailability = sendAvailability(item, syncDisplayState.offline),
            syncingProgress = syncDisplayState.syncingProgress,
            syncingTextValue = syncDisplayState.syncingTextValue,
            syncedUntilTextValue = syncDisplayState.syncedUntilTextValue,
            failedIconVisible = syncDisplayState.failedIconVisible,
            badge = wallet.badge,
            swapAvailability = swapAvailability(item, syncDisplayState.offline),
            errorMessage = errorMessage,
            syncErrorBlockedByProvider = networkAvailable && syncError?.isBlockedByProvider() == true,
            syncErrorFromDevice = networkAvailable && deviceErrorMessage != null,
            isWatchAccount = watchAccount,
            isSwipeToDeleteEnabled = isSwipeToDeleteEnabled,
            stackingUnpaid = stackingUnpaid,
            displayDiffOptionType = displayDiffOptionType,
            offline = syncDisplayState.offline,
            notSupportedOnPlatform = item.notSupportedOnPlatform,
        )
    }

    private fun sendAvailability(item: BalanceItem, offline: Boolean): OperationAvailability =
        availability(item.sendAllowed, offline, item.supports(PlatformOperation.Send))

    private fun swapAvailability(item: BalanceItem, offline: Boolean): OperationAvailability =
        availability(item.state !is AdapterState.NotSynced, offline, item.supports(PlatformOperation.Swap))

    private fun BalanceItem.supports(operation: PlatformOperation): Boolean =
        !notSupportedOnPlatform && platformOperationSupport.supports(operation)

    private fun getFullDiff(
        item: BalanceItem,
        displayDiffOptionType: DisplayDiffOptionType,
        currency: Currency,
    ): String {
        val latestRate = item.coinPrice
        val diffPercentage = item.coinPrice?.diffPercentage
        val currentPrice = latestRate?.value ?: BigDecimal.ZERO

        val sign = diffPercentage?.diffSign() ?: ""

        val diffPercentageText = if (displayDiffOptionType.hasPercentChange) {
            diffText(diffPercentage)
        } else {
            ""
        }

        val diffCurrencyText = if (displayDiffOptionType.hasPriceChange && diffPercentage != null) {

            val percentDecimal = diffPercentage.divide(BigDecimal(100))
            val previousPrice = tryOrNull {
                currentPrice.divide(
                    BigDecimal.ONE + percentDecimal,
                    10,
                    RoundingMode.HALF_UP
                )
            } ?: return "-100%"
            val priceChange = currentPrice - previousPrice

            val formattedPriceChange =
                numberFormatter.formatFiatFull(priceChange.abs(), currency.symbol)
            val signedFormattedPrice = "$sign$formattedPriceChange"

            if (displayDiffOptionType == DisplayDiffOptionType.BOTH) {
                " ($signedFormattedPrice)"
            } else {
                signedFormattedPrice
            }
        } else {
            ""
        }

        return "$diffPercentageText$diffCurrencyText"
    }
}

private const val MEBIBYTE = 1024 * 1024
