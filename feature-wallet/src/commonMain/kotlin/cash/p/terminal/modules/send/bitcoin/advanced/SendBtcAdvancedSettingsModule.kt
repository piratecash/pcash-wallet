package cash.p.terminal.modules.send.bitcoin.advanced

import cash.p.terminal.entities.TransactionDataSortMode
import io.horizontalsystems.core.entities.BlockchainType
import org.koin.core.scope.Scope

internal fun Scope.sendBtcAdvancedSettingsViewModel(blockchainType: BlockchainType) =
    SendBtcAdvancedSettingsViewModel(blockchainType, get(), get())

object SendBtcAdvancedSettingsModule {

    data class UiState(
        val transactionSortOptions: List<SortModeViewItem>,
        val transactionSortTitle: String,
        val utxoExpertModeEnabled: Boolean,
        val rbfEnabled: Boolean,
    )

    data class SortModeViewItem(
        val mode: TransactionDataSortMode,
        val selected: Boolean,
    )
}
