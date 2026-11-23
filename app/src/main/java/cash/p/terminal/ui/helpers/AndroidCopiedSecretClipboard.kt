package cash.p.terminal.ui.helpers

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import cash.p.terminal.core.COPIED_SECRET_LIFETIME_MILLIS
import cash.p.terminal.core.CopiedSecretClipboard
import cash.p.terminal.core.CopiedSecretState

/**
 * The deadline travels in the clip itself: a clip copied since has no such extra (so it is never wiped),
 * and a restarted process can still finish the clear.
 */
class AndroidCopiedSecretClipboard(private val clipboard: ClipboardManager) : CopiedSecretClipboard {

    fun copy(text: String) {
        val clip = ClipData.newPlainText("", text).apply {
            description.extras = PersistableBundle().apply {
                // Named, not ClipDescription.EXTRA_IS_SENSITIVE: that constant is API 33, Gboard honours it earlier.
                putBoolean(EXTRA_IS_SENSITIVE, true)
                putLong(EXTRA_EXPIRES_AT, System.currentTimeMillis() + COPIED_SECRET_LIFETIME_MILLIS)
            }
        }
        clipboard.setPrimaryClip(clip)
    }

    override fun copiedSecretState(): CopiedSecretState {
        // Since Android 10 a background app reads null here.
        val description = clipboard.primaryClipDescription ?: return CopiedSecretState.Unreadable
        val expiresAt = description.extras?.getLong(EXTRA_EXPIRES_AT, 0L) ?: 0L
        return if (expiresAt > 0L) {
            CopiedSecretState.Pending(expiresAt - System.currentTimeMillis())
        } else {
            CopiedSecretState.NotOurs
        }
    }

    override fun clear() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    private companion object {
        const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
        const val EXTRA_EXPIRES_AT = "cash.p.terminal.COPIED_SECRET_EXPIRES_AT"
    }
}
