package cash.p.terminal.modules.blockchainstatus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSectionTest {

    @Test
    fun buildLogGroups_entryLongerThanLimit_truncatesWithEllipsis() {
        val longMessage = "x".repeat(MAX_ENTRY_CHARS + 50)
        val logs = mapOf("tag" to mapOf("1" to longMessage))

        val entry = buildLogGroups(logs).single().entries.single()

        assertTrue(entry.text.endsWith("…"))
        assertEquals(longMessage.take(MAX_ENTRY_CHARS), entry.text.removeSuffix("…"))
    }

    @Test
    fun buildLogGroups_entryWithinLimit_isUnchanged() {
        val logs = mapOf("tag" to mapOf("1" to "short message"))

        val groups = buildLogGroups(logs)

        assertEquals(listOf(LogLine(1, "short message")), groups.single().entries)
    }

    @Test
    fun buildLogGroups_actionId_capitalizesFirstChar() {
        val logs = mapOf("zcash-sync" to mapOf("1" to "entry"))

        val groups = buildLogGroups(logs)

        assertEquals("Zcash-sync", groups.single().title)
    }

    @Test
    fun buildCopyText_entriesWithinBudget_returnsStatusTextAndAllEntriesWithNoMarker() {
        val groups = listOf(LogGroup("tag", listOf(LogLine(1, "entry1"), LogLine(2, "entry2"))))

        val result = buildCopyText("STATUS\n", groups)

        assertTrue(result.startsWith("STATUS\n"))
        assertTrue(result.contains("entry1"))
        assertTrue(result.contains("entry2"))
        assertFalse(result.contains("omitted"))
    }

    @Test
    fun buildCopyText_noLogGroups_returnsStatusTextUnchanged() {
        assertEquals("STATUS\n", buildCopyText("STATUS\n", emptyList()))
    }

    @Test
    fun buildCopyText_manyStackTraceSizedEntries_staysWithinCapKeepsNewestAndMarksOmission() {
        val stackTraceSizedEntry = "x".repeat(MAX_ENTRY_CHARS)
        val entries = (1..500).map { LogLine(it.toLong(), "$stackTraceSizedEntry-$it") }
        val group = LogGroup("tag", entries)

        val result = buildCopyText("STATUS\n", listOf(group))

        assertTrue(result.length <= MAX_COPY_CHARS)
        assertTrue(result.contains("older entries omitted — use Share for the full log"))
        assertTrue(result.contains("-500\n"))
        assertFalse(result.contains("  $stackTraceSizedEntry-1\n"))
    }

    @Test
    fun buildCopyText_interleavedGroupsOverCap_keepsNewestGlobalIdAndDropsOldestFirst() {
        val stackTraceSizedEntry = "x".repeat(MAX_ENTRY_CHARS)
        val groupA = LogGroup(
            "a",
            listOf(LogLine(1, "$stackTraceSizedEntry-1"), LogLine(101, "$stackTraceSizedEntry-101")),
        )
        val groupB = LogGroup("b", (2..100).map { LogLine(it.toLong(), "$stackTraceSizedEntry-$it") })

        val result = buildCopyText("STATUS\n", listOf(groupA, groupB))

        assertTrue(result.length <= MAX_COPY_CHARS)
        // newest id (101) lives in group A, the group that comes first — a position-based
        // (not id-based) drop policy would discard it entirely instead of the truly oldest ids.
        assertTrue(result.contains("-101\n"))
        assertFalse(result.contains("-1\n"))
        assertFalse(result.contains("-2\n"))
    }

    @Test
    fun buildCopyText_statusTextAloneOverCap_resultBoundedToMaxCopyChars() {
        val hugeStatus = "x".repeat(MAX_COPY_CHARS + 1)

        assertEquals(MAX_COPY_CHARS, buildCopyText(hugeStatus, emptyList()).length)
    }

    @Test
    fun buildCopyText_statusTextOverCapWithLogs_resultBoundedToMaxCopyChars() {
        val hugeStatus = "x".repeat(MAX_COPY_CHARS + 1)
        val groups = listOf(LogGroup("tag", listOf(LogLine(1, "entry"))))

        assertTrue(buildCopyText(hugeStatus, groups).length <= MAX_COPY_CHARS)
    }
}
