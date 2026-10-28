package cash.p.terminal.modules.paycore.webview

import androidx.compose.runtime.Composable

/**
 * Hosts a PayCore page that finishes by redirecting to [backUrlPrefix]. Desktop has no WebView, so it opens
 * the browser and lets the user confirm with [completeTitle] instead of the redirect.
 */
interface PayCoreWebPage {
    @Composable
    fun Content(
        url: String,
        title: String,
        completeTitle: String,
        onClose: () -> Unit,
        onInterceptBackUrl: () -> Unit,
        backUrlPrefix: String,
    )
}
