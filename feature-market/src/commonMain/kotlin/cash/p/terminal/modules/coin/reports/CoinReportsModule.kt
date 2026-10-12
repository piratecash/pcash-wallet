package cash.p.terminal.modules.coin.reports

import androidx.compose.runtime.Immutable

object CoinReportsModule {
    @Immutable
    data class ReportViewItem(
        val author: String,
        val title: String,
        val body: String,
        val date: String,
        val url: String
    )
}
