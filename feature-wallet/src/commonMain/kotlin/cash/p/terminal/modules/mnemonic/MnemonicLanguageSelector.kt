package cash.p.terminal.modules.mnemonic

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.displayNameStringRes
import cash.p.terminal.ui_compose.components.AppSelectorDialog
import cash.p.terminal.ui_compose.components.AppSelectorItem
import cash.p.terminal.ui_compose.components.B2
import cash.p.terminal.ui_compose.components.D1
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.hdwalletkit.Language
import cash.p.terminal.resources.CreateWallet_Wordlist
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_down_arrow_20
import cash.p.terminal.resources.ic_globe_20

internal val mnemonicLanguagesOrdered: List<Language> = listOf(
    Language.English,
    Language.Japanese,
    Language.Spanish,
    Language.SimplifiedChinese,
    Language.TraditionalChinese,
    Language.French,
    Language.Italian,
    Language.Korean,
    Language.Czech,
    Language.Portuguese,
)

@Composable
internal fun MnemonicLanguageSelectorDialog(
    languages: List<Language>,
    selectedLanguage: Language,
    onDismissRequest: () -> Unit,
    onSelectLanguage: (Language) -> Unit
) {
    AppSelectorDialog(
        title = stringResource(Res.string.CreateWallet_Wordlist),
        items = languages.map {
            AppSelectorItem(
                stringResource(it.displayNameStringRes),
                it == selectedLanguage,
                it
            )
        },
        onDismiss = onDismissRequest,
        onSelect = onSelectLanguage
    )
}

@Composable
internal fun MnemonicLanguageCell(
    language: Language,
    showLanguageSelectorDialog: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    RowUniversal(
        modifier = modifier.padding(horizontal = 16.dp),
        onClick = if (enabled) showLanguageSelectorDialog else null
    ) {
        val iconTint = if (enabled) ComposeAppTheme.colors.iconSecondary else ComposeAppTheme.colors.iconDisabled
        Icon(
            painter = painterResource(Res.drawable.ic_globe_20),
            contentDescription = null,
            tint = iconTint
        )
        B2(
            text = stringResource(Res.string.CreateWallet_Wordlist),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.weight(1f))
        D1(
            text = stringResource(language.displayNameStringRes),
            textColor = if (enabled) ComposeAppTheme.colors.textSecondary else ComposeAppTheme.colors.textDisabled,
        )
        Icon(
            modifier = Modifier.padding(start = 4.dp),
            painter = painterResource(Res.drawable.ic_down_arrow_20),
            contentDescription = null,
            tint = iconTint
        )
    }
}
