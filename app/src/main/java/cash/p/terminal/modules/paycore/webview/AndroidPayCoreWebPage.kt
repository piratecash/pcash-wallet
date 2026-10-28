package cash.p.terminal.modules.paycore.webview

import androidx.compose.runtime.Composable

class AndroidPayCoreWebPage : PayCoreWebPage {
    @Composable
    override fun Content(
        url: String,
        title: String,
        completeTitle: String,
        onClose: () -> Unit,
        onInterceptBackUrl: () -> Unit,
        backUrlPrefix: String,
    ) = PayCoreWebViewScreen(
        url = url,
        title = title,
        onClose = onClose,
        onInterceptBackUrl = onInterceptBackUrl,
        backUrlPrefix = backUrlPrefix,
    )
}
