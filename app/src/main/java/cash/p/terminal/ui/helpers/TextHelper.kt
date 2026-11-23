package cash.p.terminal.ui.helpers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import cash.p.terminal.core.App
import cash.p.terminal.core.CopiedSecretCleaner
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.core.getKoinInstance
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

object TextHelper : IClipboardManager {

    private val secretClipboard by lazy { clipboard?.let(::AndroidCopiedSecretClipboard) }
    private val secretCleaner by lazy {
        secretClipboard?.let {
            CopiedSecretCleaner(it, CoroutineScope(SupervisorJob() + getKoinInstance<DispatcherProvider>().main))
        }
    }

    override val hasPrimaryClip: Boolean
        get() = clipboard?.hasPrimaryClip() ?: false

    override fun copyText(text: String) {
        copyTextToClipboard(App.instance, text)
    }

    override fun copySecret(text: String) {
        secretClipboard?.copy(text)
        secretCleaner?.check()
    }

    /** Android lets only the focused window read the clipboard, so a deadline missed without focus waits for it. */
    fun clearCopiedSecretIfExpired() {
        secretCleaner?.check()
    }

    override fun getCopiedText(): String {
        return clipboard?.primaryClip?.itemCount?.let { count ->
            if (count > 0) {
                clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
            } else {
                null
            }
        } ?: ""
    }

    fun getCleanedUrl(link: String): String{
        var cleanedUrl = link.replaceFirst("^(http[s]?://www\\.|http[s]?://|www\\.)".toRegex(),"")
        if (cleanedUrl.endsWith("/")) {
            cleanedUrl = cleanedUrl.substring(0, cleanedUrl.length - 1)
        }
        return cleanedUrl
    }

    private val clipboard: ClipboardManager?
        get() = App.instance.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private fun copyTextToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("text", text)
        clipboard?.setPrimaryClip(clip)
    }

}
