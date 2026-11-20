package io.horizontalsystems.core

import cash.p.terminal.core.COPIED_SECRET_LIFETIME_MILLIS
import cash.p.terminal.core.CopiedSecretClipboard
import cash.p.terminal.core.CopiedSecretState
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.UnsupportedFlavorException

/**
 * Confined to the Swing EDT together with every copy, so the state is a plain field.
 * After a failed native write AWT's cache lies, so a secret may survive then, but foreign text is never wiped.
 */
class AwtCopiedSecretClipboard(private val clipboard: Clipboard) : CopiedSecretClipboard {

    private data class Copied(val text: String, val expiresAt: Long)

    private var copied: Copied? = null

    fun copy(text: String) {
        clipboard.setContents(StringSelection(text), null)
        copied = Copied(text, System.currentTimeMillis() + COPIED_SECRET_LIFETIME_MILLIS)
    }

    override fun copiedSecretState(): CopiedSecretState {
        val pending = copied ?: return CopiedSecretState.NotOurs
        if (readText() == pending.text) {
            return CopiedSecretState.Pending(pending.expiresAt - System.currentTimeMillis())
        }
        copied = null
        return CopiedSecretState.NotOurs
    }

    override fun clear() {
        clipboard.setContents(StringSelection(""), null)
        copied = null
    }

    fun clearIfStillCopied() {
        if (copiedSecretState() is CopiedSecretState.Pending) clear()
    }

    private fun readText(): String? = try {
        clipboard.getData(DataFlavor.stringFlavor) as? String
    } catch (e: UnsupportedFlavorException) {
        null
    }
}
