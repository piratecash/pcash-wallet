package cash.p.terminal.modules.mnemonic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import cash.p.terminal.core.displayNameStringRes
import cash.p.terminal.modules.settings.checklistterms.GeneralTermsContent
import io.horizontalsystems.hdwalletkit.Language
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.Button_IAgree
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.non_english_mnemonic_terms_checkboxes
import cash.p.terminal.resources.non_english_mnemonic_terms_title
import cash.p.terminal.resources.non_english_mnemonic_terms_warning
import cash.p.terminal.resources.non_english_mnemonic_terms_warning_title
import cash.p.terminal.resources.non_english_mnemonic_terms_warning_unsupported_bold

@Composable
internal fun NonEnglishMnemonicTermsScreen(
    language: Language,
    onConfirm: () -> Unit,
    onBack: () -> Unit
) {
    GeneralTermsContent(
        termsStrings = stringArrayResource(Res.array.non_english_mnemonic_terms_checkboxes).toList(),
        title = stringResource(Res.string.non_english_mnemonic_terms_title),
        confirmButtonText = stringResource(Res.string.Button_IAgree),
        onClose = onBack,
        onConfirm = onConfirm,
        warning = nonEnglishMnemonicTermsWarning(language),
        warningTitle = stringResource(Res.string.non_english_mnemonic_terms_warning_title),
        onBack = onBack,
        onCancel = onBack,
        cancelButtonText = stringResource(Res.string.Button_Cancel)
    )
}

@Composable
private fun nonEnglishMnemonicTermsWarning(language: Language): AnnotatedString {
    val languageName = stringResource(language.displayNameStringRes)
    val unsupportedPhrase = stringResource(
        Res.string.non_english_mnemonic_terms_warning_unsupported_bold
    )
    val bip39Language = "BIP39 $languageName"
    val warning = stringResource(
        Res.string.non_english_mnemonic_terms_warning,
        languageName,
        languageName
    )

    return remember(warning, languageName, unsupportedPhrase, bip39Language) {
        buildAnnotatedString {
            append(warning)
            addBoldStyleToAll(warning, languageName)
            addBoldStyleToAll(warning, unsupportedPhrase)
            addBoldStyleToAll(warning, bip39Language)
        }
    }
}

private fun AnnotatedString.Builder.addBoldStyleToAll(text: String, value: String) {
    var startIndex = text.indexOf(value)
    while (startIndex >= 0) {
        addStyle(
            style = SpanStyle(fontWeight = FontWeight.Bold),
            start = startIndex,
            end = startIndex + value.length
        )
        startIndex = text.indexOf(value, startIndex + value.length)
    }
}
