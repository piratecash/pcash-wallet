package cash.p.terminal.modules.coin.detectors

import cash.p.terminal.resources.Detectors_GeneralDetectors
import cash.p.terminal.resources.Detectors_TokenDetectors
import cash.p.terminal.resources.Res
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import org.jetbrains.compose.resources.StringResource

object DetectorsModule {
    data class UiState(
        val title: String,
        val coreIssues: List<IssueViewItem>,
        val generalIssues: List<IssueViewItem>
    )

    enum class DetectorsTab(val title: StringResource) {
        Token(Res.string.Detectors_TokenDetectors),
        General(Res.string.Detectors_GeneralDetectors);
    }

    data class IssueViewItem(
        val id: Int,
        val issue: IssueParcelable,
        val expanded: Boolean = false
    )
}

@CommonParcelize
data class IssueParcelable(
    val issue: String,
    val title: String? = null,
    val description: String,
    val issues: List<IssueItemParcelable>? = null,
) : CommonParcelable

@CommonParcelize
data class IssueItemParcelable(
    val impact: String,
    val confidence: String? = null,
    val description: String,
) : CommonParcelable
