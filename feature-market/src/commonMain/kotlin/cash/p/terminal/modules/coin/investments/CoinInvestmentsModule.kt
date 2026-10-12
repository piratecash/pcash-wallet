package cash.p.terminal.modules.coin.investments

import androidx.compose.runtime.Immutable

object CoinInvestmentsModule {
    @Immutable
    data class ViewItem(
        val amount: String,
        val info: String,
        val fundViewItems: List<FundViewItem>
    )

    @Immutable
    data class FundViewItem(
        val name: String,
        val logoUrl: String,
        val isLead: Boolean,
        val url: String
    )
}
