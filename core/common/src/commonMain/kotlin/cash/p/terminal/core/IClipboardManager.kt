package cash.p.terminal.core

interface IClipboardManager {
    fun copyText(text: String)

    /** For seeds and private keys: marked sensitive, cleared after a minute if still there. Main thread only. */
    fun copySecret(text: String)
    fun getCopiedText(): String
    val hasPrimaryClip: Boolean
}
