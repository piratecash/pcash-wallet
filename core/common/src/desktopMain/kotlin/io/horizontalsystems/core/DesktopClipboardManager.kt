package io.horizontalsystems.core

import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.core.tryOrNull
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

class DesktopClipboardManager : IClipboardManager {
    private val clipboard get() = Toolkit.getDefaultToolkit().systemClipboard

    override val hasPrimaryClip: Boolean
        get() = tryOrNull { clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor) } ?: false

    override fun copyText(text: String) {
        val selection = StringSelection(text)
        clipboard.setContents(selection, selection)
    }

    override fun getCopiedText(): String =
        tryOrNull { clipboard.getData(DataFlavor.stringFlavor) as? String }.orEmpty()
}
