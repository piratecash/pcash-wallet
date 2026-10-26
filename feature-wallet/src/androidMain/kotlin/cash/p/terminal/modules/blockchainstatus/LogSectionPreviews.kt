package cash.p.terminal.modules.blockchainstatus

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Preview
@Composable
private fun LogSectionPreview() {
    ComposeAppTheme {
        LazyColumn {
            logSection(
                listOf(
                    LogGroup(
                        "Zcash-sync",
                        listOf(LogLine(1, "Started sync"), LogLine(2, "Downloaded 100 blocks")),
                    ),
                    LogGroup("Bitcoin-send", listOf(LogLine(3, "Broadcast tx abc123"))),
                )
            )
        }
    }
}
