package io.horizontalsystems.core

import cash.p.terminal.core.COPIED_SECRET_LIFETIME_MILLIS
import cash.p.terminal.core.CopiedSecretState
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AwtCopiedSecretClipboardTest {

    /** Mirrors SunClipboard: the cached contents change before the native write, which may then fail. */
    private class BusyClipboard : Clipboard("test") {
        var failNextWrite = false
        var writes = 0

        override fun setContents(contents: Transferable, owner: ClipboardOwner?) {
            writes++
            super.setContents(contents, owner)
            if (failNextWrite) {
                failNextWrite = false
                throw IllegalStateException("cannot open system clipboard")
            }
        }
    }

    private val clipboard = BusyClipboard()
    private val secretClipboard = AwtCopiedSecretClipboard(clipboard)

    private fun clipboardText() = clipboard.getData(DataFlavor.stringFlavor) as String

    @Test
    fun copiedSecretState_afterCopy_pendingForAboutAMinute() {
        secretClipboard.copy(SECRET)

        val state = assertIs<CopiedSecretState.Pending>(secretClipboard.copiedSecretState())
        assertTrue(state.remainingMillis in 1..COPIED_SECRET_LIFETIME_MILLIS)
        assertEquals(SECRET, clipboardText())
    }

    @Test
    fun copiedSecretState_otherTextCopiedAfter_notOurs() {
        secretClipboard.copy(SECRET)
        clipboard.setContents(StringSelection("address"), null)

        assertEquals(CopiedSecretState.NotOurs, secretClipboard.copiedSecretState())
    }

    @Test
    fun copiedSecretState_nonTextContents_notOurs() {
        secretClipboard.copy(SECRET)
        clipboard.setContents(object : Transferable {
            override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
            override fun getTransferData(flavor: DataFlavor) = throw UnsupportedFlavorException(flavor)
        }, null)

        assertEquals(CopiedSecretState.NotOurs, secretClipboard.copiedSecretState())
    }

    @Test
    fun copiedSecretState_nothingCopied_notOurs() {
        assertEquals(CopiedSecretState.NotOurs, secretClipboard.copiedSecretState())
    }

    @Test
    fun clear_secretOnClipboard_emptiesIt() {
        secretClipboard.copy(SECRET)

        secretClipboard.clear()

        assertEquals("", clipboardText())
        assertEquals(CopiedSecretState.NotOurs, secretClipboard.copiedSecretState())
    }

    @Test
    fun clearIfStillCopied_foreignText_leavesIt() {
        secretClipboard.copy(SECRET)
        clipboard.setContents(StringSelection("address"), null)

        secretClipboard.clearIfStillCopied()

        assertEquals("address", clipboardText())
    }

    @Test
    fun clearIfStillCopied_secretStillThere_clears() {
        secretClipboard.copy(SECRET)

        secretClipboard.clearIfStillCopied()

        assertEquals("", clipboardText())
    }

    @Test
    fun clearIfStillCopied_afterFailedWriteOfOtherText_writesNothing() {
        secretClipboard.copy(SECRET)
        clipboard.failNextWrite = true
        assertFailsWith<IllegalStateException> { clipboard.setContents(StringSelection("xpub"), null) }
        val writesBefore = clipboard.writes

        secretClipboard.clearIfStillCopied()

        assertEquals(writesBefore, clipboard.writes)
    }

    private companion object {
        const val SECRET = "abandon ability able about above absent absorb abstract absurd abuse access accident"
    }
}
