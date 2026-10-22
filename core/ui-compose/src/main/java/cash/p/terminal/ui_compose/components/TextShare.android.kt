package cash.p.terminal.ui_compose.components

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberTextShare(): TextShare {
    val context = LocalContext.current
    return remember(context) { IntentTextShare(context) }
}

private class IntentTextShare(private val context: Context) : TextShare {
    override val isAvailable = true

    override fun share(text: String) {
        context.startActivity(Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        })
    }
}
