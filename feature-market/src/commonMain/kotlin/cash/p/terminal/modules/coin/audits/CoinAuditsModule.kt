package cash.p.terminal.modules.coin.audits

import androidx.compose.runtime.Immutable
import cash.p.terminal.strings.helpers.TranslatableString
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize

object CoinAuditsModule {
    @Immutable
    data class AuditorViewItem(
        val name: String,
        val logoUrl: String,
        val auditViewItems: List<AuditViewItem>
    )

    @Immutable
    data class AuditViewItem(
        val date: String?,
        val name: String,
        val issues: TranslatableString,
        val reportUrl: String?
    )

    data class UiState(
        val auditors: List<AuditorViewItem>
    )

    @CommonParcelize
    data class AuditParcelable(
        val date: String?,
        val name: String?,
        val auditUrl: String?,
        val techIssues: Int,
        val partnerName: String?
    ) : CommonParcelable
}
