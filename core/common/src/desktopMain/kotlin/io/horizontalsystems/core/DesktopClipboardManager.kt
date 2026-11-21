package io.horizontalsystems.core

import cash.p.terminal.core.CopiedSecretCleaner
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.core.tryOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

class DesktopClipboardManager(private val dispatcherProvider: DispatcherProvider) : IClipboardManager {
    private val clipboard get() = Toolkit.getDefaultToolkit().systemClipboard

    private val secretClipboard by lazy { AwtCopiedSecretClipboard(clipboard) }
    private val secretCleaner by lazy {
        CopiedSecretCleaner(secretClipboard, CoroutineScope(SupervisorJob() + dispatcherProvider.main))
    }

    override val hasPrimaryClip: Boolean
        get() = tryOrNull { clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor) } ?: false

    override fun copyText(text: String) {
        val selection = StringSelection(text)
        clipboard.setContents(selection, selection)
    }

    override fun copySecret(text: String) {
        secretClipboard.copy(text)
        secretCleaner.check()
    }

    override fun getCopiedText(): String =
        tryOrNull { clipboard.getData(DataFlavor.stringFlavor) as? String }.orEmpty()

    /** On quit, after the windows are gone. Call on the EDT. */
    fun clearCopiedSecret() {
        tryOrNull { secretClipboard.clearIfStillCopied() }
    }
}
