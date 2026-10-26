package cash.p.terminal.modules.syncerror

import androidx.compose.runtime.Composable
import java.awt.Desktop
import java.io.IOException
import java.net.URI
import java.net.URLEncoder

@Composable
internal actual fun rememberMailComposer(): (email: String, body: String) -> Boolean = ::composeMail

private fun composeMail(email: String, body: String): Boolean {
    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) return false
    // mailto wants %20 for spaces; URLEncoder writes form encoding with '+'.
    val encodedBody = URLEncoder.encode(body, Charsets.UTF_8).replace("+", "%20")
    return try {
        Desktop.getDesktop().mail(URI("mailto:$email?body=$encodedBody"))
        true
    } catch (_: IOException) {
        false
    }
}
