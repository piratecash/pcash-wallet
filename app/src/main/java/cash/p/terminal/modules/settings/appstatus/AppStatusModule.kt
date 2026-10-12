package cash.p.terminal.modules.settings.appstatus

import cash.p.terminal.modules.blockchainstatus.LogGroup
import cash.p.terminal.modules.blockchainstatus.StatusSection

object AppStatusModule {
    sealed class BlockContent {
        data class TitleValue(val title: String, val value: String) : BlockContent()
    }

    data class BlockData(val title: String?, val content: List<BlockContent>)

    data class UiState(
        val appStatusAsText: String?,
        val blockViewItems: List<BlockData>,
        val logGroups: List<LogGroup>,
        val blockchainStatusSections: List<StatusSection>,
        val loading: Boolean,
        val shareInProgress: Boolean,
    )
}
