package cash.p.terminal.modules.walletconnect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.wallet_connect_verify_mismatch
import cash.p.terminal.resources.wallet_connect_verify_mismatch_no_origin
import cash.p.terminal.resources.wallet_connect_verify_mismatch_title
import cash.p.terminal.resources.wallet_connect_verify_scam
import cash.p.terminal.resources.wallet_connect_verify_scam_title
import cash.p.terminal.resources.wallet_connect_verify_unknown
import cash.p.terminal.resources.wallet_connect_verify_unknown_title
import cash.p.terminal.ui.helpers.TextHelper
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun WCVerifyAlertCard(alert: WCVerifyAlert?, modifier: Modifier = Modifier) {
    when (alert) {
        null -> return
        WCVerifyAlert.Scam -> TextImportantError(
            modifier = modifier,
            title = stringResource(Res.string.wallet_connect_verify_scam_title),
            text = stringResource(Res.string.wallet_connect_verify_scam),
        )

        is WCVerifyAlert.Impersonation -> TextImportantError(
            modifier = modifier,
            title = stringResource(Res.string.wallet_connect_verify_mismatch_title),
            text = alert.origin
                ?.let { stringResource(Res.string.wallet_connect_verify_mismatch, TextHelper.getCleanedUrl(it)) }
                ?: stringResource(Res.string.wallet_connect_verify_mismatch_no_origin),
        )

        WCVerifyAlert.Unverified -> TextImportantWarning(
            modifier = modifier,
            title = stringResource(Res.string.wallet_connect_verify_unknown_title),
            text = stringResource(Res.string.wallet_connect_verify_unknown),
        )
    }
    VSpacer(16.dp)
}

@Preview
@Composable
private fun WCVerifyAlertCardPreview() {
    ComposeAppTheme {
        Column {
            listOf(
                WCVerifyAlert.Scam,
                WCVerifyAlert.Impersonation("https://app.uniswap.org.evil.example"),
                WCVerifyAlert.Impersonation(null),
                WCVerifyAlert.Unverified,
            ).forEach { WCVerifyAlertCard(it, Modifier.padding(horizontal = 16.dp)) }
        }
    }
}
