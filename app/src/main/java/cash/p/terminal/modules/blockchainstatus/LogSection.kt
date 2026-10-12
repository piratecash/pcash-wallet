package cash.p.terminal.modules.blockchainstatus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionUniversalItem
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.plateBackground
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.entities.SectionItemPosition
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

const val MAX_ENTRY_CHARS = 1_000
const val MAX_COPY_CHARS = 100_000

data class LogLine(val id: Long, val text: String)
data class LogGroup(val title: String, val entries: List<LogLine>)

@Suppress("UNCHECKED_CAST")
fun buildLogGroups(logs: Map<String, Any>): List<LogGroup> =
    logs.map { (actionId, value) ->
        val entries = (value as Map<String, String>).map { (id, message) ->
            LogLine(id.toLong(), message.truncateLogEntry())
        }
        LogGroup(actionId.replaceFirstChar(Char::uppercase), entries)
    }

private fun String.truncateLogEntry(): String =
    if (length > MAX_ENTRY_CHARS) take(MAX_ENTRY_CHARS) + "…" else this

/** Keeps the newest log entries by id, dropping the oldest first, within [MAX_COPY_CHARS] of [statusText] plus the log. */
fun buildCopyText(statusText: String, logGroups: List<LogGroup>): String {
    val totalEntries = logGroups.sumOf { it.entries.size }
    if (totalEntries == 0) return statusText.take(MAX_COPY_CHARS)

    var lo = 0
    var hi = totalEntries
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        if (renderCopyText(statusText, logGroups, mid).length <= MAX_COPY_CHARS) {
            lo = mid
        } else {
            hi = mid - 1
        }
    }
    return renderCopyText(statusText, logGroups, lo).take(MAX_COPY_CHARS)
}

private fun renderCopyText(statusText: String, logGroups: List<LogGroup>, keepNewest: Int): String {
    val totalEntries = logGroups.sumOf { it.entries.size }
    val omitted = totalEntries - keepNewest
    val keptGroups = keepNewestEntries(logGroups, keepNewest)
    return buildString {
        append(statusText)
        if (omitted > 0) {
            appendLine()
            appendLine("… $omitted older entries omitted — use Share for the full log")
        }
        keptGroups.forEach { group ->
            appendLine(group.title)
            group.entries.forEach { appendLine("  ${it.text}") }
        }
    }
}

/** Keeps the [keepNewest] globally newest ids, preserving each group's own order and position. */
private fun keepNewestEntries(logGroups: List<LogGroup>, keepNewest: Int): List<LogGroup> {
    val keptIds = logGroups.asSequence()
        .flatMap { it.entries.asSequence() }
        .sortedByDescending { it.id }
        .take(keepNewest)
        .mapTo(mutableSetOf()) { it.id }

    return logGroups.mapNotNull { group ->
        group.entries.filter { it.id in keptIds }
            .takeIf { it.isNotEmpty() }
            ?.let { group.copy(entries = it) }
    }
}

fun LazyListScope.logSection(logGroups: List<LogGroup>) {
    if (logGroups.isEmpty()) return

    item { InfoText(text = "APP LOG") }
    logGroups.forEach { group ->
        val rowCount = group.entries.size + 1
        item {
            VSpacer(12.dp)
            LogSectionRow(sectionItemPosition(0, rowCount)) { subhead2_leah(text = group.title) }
        }
        itemsIndexed(group.entries) { index, entry ->
            LogSectionRow(sectionItemPosition(index + 1, rowCount)) { subhead2_grey(text = entry.text) }
        }
    }
    item { VSpacer(12.dp) }
}

private fun sectionItemPosition(index: Int, count: Int): SectionItemPosition = when {
    count == 1 -> SectionItemPosition.Single
    index == 0 -> SectionItemPosition.First
    index == count - 1 -> SectionItemPosition.Last
    else -> SectionItemPosition.Middle
}

/** One row of a section split across lazy items: only the outer rows of the group are rounded. */
@Composable
private fun LogSectionRow(position: SectionItemPosition, content: @Composable () -> Unit) {
    val shape = when (position) {
        SectionItemPosition.First -> RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
        SectionItemPosition.Last -> RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
        SectionItemPosition.Single -> RoundedCornerShape(12.dp)
        SectionItemPosition.Middle -> RectangleShape
    }
    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(plateBackground())
    ) {
        SectionUniversalItem(
            borderTop = position == SectionItemPosition.Middle || position == SectionItemPosition.Last,
        ) {
            RowUniversal(modifier = Modifier.padding(horizontal = 16.dp)) { content() }
        }
    }
}

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
