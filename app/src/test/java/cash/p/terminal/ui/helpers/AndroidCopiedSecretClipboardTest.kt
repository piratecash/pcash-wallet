package cash.p.terminal.ui.helpers

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.COPIED_SECRET_LIFETIME_MILLIS
import cash.p.terminal.core.CopiedSecretState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AndroidCopiedSecretClipboardTest {

    private val clipboard = ApplicationProvider.getApplicationContext<Context>()
        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val secretClipboard = AndroidCopiedSecretClipboard(clipboard)

    @Test
    fun copy_secret_marksClipSensitiveWithoutLabel() {
        secretClipboard.copy(SECRET)

        val clip = requireNotNull(clipboard.primaryClip)
        assertEquals(SECRET, clip.getItemAt(0).text)
        assertEquals("", clip.description.label)
        assertTrue(clip.description.extras.getBoolean("android.content.extra.IS_SENSITIVE"))
    }

    @Test
    fun copiedSecretState_afterCopy_pendingForAboutAMinute() {
        secretClipboard.copy(SECRET)

        val state = secretClipboard.copiedSecretState() as CopiedSecretState.Pending
        assertTrue(state.remainingMillis in 1..COPIED_SECRET_LIFETIME_MILLIS)
    }

    @Test
    fun copiedSecretState_plainClipCopiedAfter_notOurs() {
        secretClipboard.copy(SECRET)
        clipboard.setPrimaryClip(ClipData.newPlainText("text", "address"))

        assertEquals(CopiedSecretState.NotOurs, secretClipboard.copiedSecretState())
    }

    @Test
    fun copiedSecretState_emptyClipboard_unreadable() {
        assertEquals(CopiedSecretState.Unreadable, secretClipboard.copiedSecretState())
    }

    @Test
    fun clear_secretOnClipboard_removesIt() {
        secretClipboard.copy(SECRET)

        secretClipboard.clear()

        assertTrue(clipboard.primaryClip?.getItemAt(0)?.text.isNullOrEmpty())
    }

    @Test
    @Config(sdk = [27])
    fun clear_api27_overwritesWithEmptyClip() {
        secretClipboard.copy(SECRET)

        secretClipboard.clear()

        assertEquals("", clipboard.primaryClip?.getItemAt(0)?.text.toString())
        assertEquals(CopiedSecretState.NotOurs, secretClipboard.copiedSecretState())
    }

    private companion object {
        const val SECRET = "abandon ability able about above absent absorb abstract absurd abuse access accident"
    }
}
