package cash.p.terminal.modules.paycore.verification

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.paycore_verification_error
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun PhoneInputScreenPreview() {
    ComposeAppTheme {
        PayCoreVerificationScreenContent(
            uiState = PayCoreVerificationUiState(screen = VerificationScreen.PhoneInput),
            onClose = {},
            onPhoneChange = {},
            onContinueClick = {},
            onAcceptVerificationWarning = {},
            onKycClick = {},
            onRetry = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun PhoneInputScreenErrorPreview() {
    ComposeAppTheme {
        PayCoreVerificationScreenContent(
            uiState = PayCoreVerificationUiState(
                screen = VerificationScreen.PhoneInput,
                phone = "9045556478",
                error = stringResource(Res.string.paycore_verification_error)
            ),
            onClose = {},
            onPhoneChange = {},
            onContinueClick = {},
            onAcceptVerificationWarning = {},
            onKycClick = {},
            onRetry = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun ProcessingScreenPreview() {
    ComposeAppTheme {
        PayCoreVerificationScreenContent(
            uiState = PayCoreVerificationUiState(
                screen = VerificationScreen.Processing,
                loading = true
            ),
            onClose = {},
            onPhoneChange = {},
            onContinueClick = {},
            onAcceptVerificationWarning = {},
            onKycClick = {},
            onRetry = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun KycRequiredScreenPreview() {
    ComposeAppTheme {
        PayCoreVerificationScreenContent(
            uiState = PayCoreVerificationUiState(
                screen = VerificationScreen.KycRequired,
                kycUrl = "https://example.com/kyc"
            ),
            onClose = {},
            onPhoneChange = {},
            onContinueClick = {},
            onAcceptVerificationWarning = {},
            onKycClick = {},
            onRetry = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun PhoneInputSupportRequiredPreview() {
    ComposeAppTheme {
        PayCoreVerificationScreenContent(
            uiState = PayCoreVerificationUiState(
                screen = VerificationScreen.PhoneInput,
                phone = "9045556478",
                supportRequired = true
            ),
            onClose = {},
            onPhoneChange = {},
            onContinueClick = {},
            onAcceptVerificationWarning = {},
            onKycClick = {},
            onRetry = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun VerificationWarningScreenPreview() {
    ComposeAppTheme {
        PayCoreVerificationScreenContent(
            uiState = PayCoreVerificationUiState(screen = VerificationScreen.VerificationWarning),
            onClose = {},
            onPhoneChange = {},
            onContinueClick = {},
            onAcceptVerificationWarning = {},
            onKycClick = {},
            onRetry = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun VerificationWarningScreenDirectPreview() {
    ComposeAppTheme {
        VerificationWarningScreen(
            paddingValues = PaddingValues(),
            onAccept = {},
            onBack = {}
        )
    }
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun ErrorScreenPreview() {
    ComposeAppTheme {
        PayCoreVerificationScreenContent(
            uiState = PayCoreVerificationUiState(screen = VerificationScreen.Error),
            onClose = {},
            onPhoneChange = {},
            onContinueClick = {},
            onAcceptVerificationWarning = {},
            onKycClick = {},
            onRetry = {}
        )
    }
}
