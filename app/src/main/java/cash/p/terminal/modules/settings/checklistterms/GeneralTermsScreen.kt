package cash.p.terminal.modules.settings.checklistterms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
internal fun GeneralTermsDialog(
    terms: List<String>,
    title: String,
    confirmButtonText: String,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    warning: AnnotatedString? = null,
    warningTitle: String? = null
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(ComposeAppTheme.colors.backgroundBase)
        ) {
            GeneralTermsContent(
                termsStrings = terms,
                title = title,
                confirmButtonText = confirmButtonText,
                onClose = onClose,
                onConfirm = onConfirm,
                warning = warning,
                warningTitle = warningTitle
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun Preview_GeneralTermsDialog() {
    ComposeAppTheme {
        GeneralTermsDialog(
            terms = listOf(
                "Terms of Service\nSecond line",
                "Privacy Policy",
                "Cookie Policy"
            ),
            title = "General Terms",
            confirmButtonText = "I Agree",
            onClose = {},
            onConfirm = {}
        )
    }
}
