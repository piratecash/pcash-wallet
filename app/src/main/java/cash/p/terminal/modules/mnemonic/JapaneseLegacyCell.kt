package cash.p.terminal.modules.mnemonic

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import cash.p.terminal.R
import cash.p.terminal.modules.restoreaccount.MnemonicImportDraft
import cash.p.terminal.ui_compose.components.CheckboxWithInfo
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.InfoBottomSheet
import cash.p.terminal.wallet.MnemonicDerivation

@Composable
fun JapaneseLegacyCell(
    draft: MnemonicImportDraft,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!draft.isJapanese) return
    var showInfo by remember { mutableStateOf(false) }
    val title = stringResource(R.string.mnemonic_legacy_option)
    val checked = draft.derivation == MnemonicDerivation.Legacy
    CheckboxWithInfo(
        title = title,
        checked = checked,
        onCheckedChange = onToggle,
        onInfoClick = { showInfo = true },
        modifier = modifier,
    )
    if (showInfo) {
        InfoBottomSheet(title = title, text = AnnotatedString(stringResource(R.string.mnemonic_legacy_info)),
            onDismiss = { showInfo = false })
    }
}

@Composable
fun JapaneseMnemonicFormat(derivation: MnemonicDerivation?, modifier: Modifier = Modifier) {
    if (derivation == null) return
    val label = if (derivation == MnemonicDerivation.Bip39)
        R.string.mnemonic_bip39_format else R.string.mnemonic_legacy_format
    Box(modifier) { InfoText(text = stringResource(label)) }
}
