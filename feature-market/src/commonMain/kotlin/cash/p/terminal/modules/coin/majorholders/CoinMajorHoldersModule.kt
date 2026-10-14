package cash.p.terminal.modules.coin.majorholders

import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.modules.coin.MajorHolderItem
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.StackBarSlice

object CoinMajorHoldersModule {
    data class UiState(
        val viewState: ViewState,
        val top10Share: String,
        val totalHoldersCount: String,
        val seeAllUrl: String?,
        val chartData: List<StackBarSlice>,
        val topHolders: List<MajorHolderItem>,
        val error: TranslatableString?,
    )
}
