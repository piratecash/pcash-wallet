package cash.p.terminal.shared.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Settings_Language
import cash.p.terminal.resources.ic_checkmark_20
import cash.p.terminal.strings.helpers.LocaleType
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.ILanguageManager
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.util.Locale

data class LanguageSettingsItem(
    val tag: String,
    val name: String,
    val nativeName: String,
    val flag: DrawableResource,
    val current: Boolean,
)

fun languageSettingsItems(languageManager: ILanguageManager): List<LanguageSettingsItem> {
    val displayLocale = languageManager.currentLocale
    val current = LocaleType.of(languageManager.currentLocaleTag)
    return LocaleType.entries.map { localeType ->
        val locale = Locale.forLanguageTag(localeType.tag)
        LanguageSettingsItem(
            tag = localeType.tag,
            name = locale.displayNameIn(displayLocale),
            nativeName = locale.displayNameIn(locale),
            flag = localeType.flag,
            current = localeType == current,
        )
    }
}

/** The settings row names the same list entry that is ticked, not the raw (possibly regional) locale. */
fun currentLanguageName(languageManager: ILanguageManager): String =
    languageSettingsItems(languageManager).firstOrNull { it.current }?.name
        ?: languageManager.currentLocale.displayNameIn(languageManager.currentLocale)

@Composable
fun LanguageSettingsContent(
    items: List<LanguageSettingsItem>,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(color = ComposeAppTheme.colors.backgroundBase)
    ) {
        AppBar(
            title = stringResource(Res.string.Settings_Language),
            navigationIcon = { HsBackButton(onClick = onBack) }
        )
        Column(
            Modifier.verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))
            CellUniversalLawrenceSection(items) { item ->
                LanguageCell(
                    title = item.name,
                    subtitle = item.nativeName,
                    icon = item.flag,
                    checked = item.current,
                    onClick = { onSelect(item.tag) }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun Locale.displayNameIn(inLocale: Locale): String =
    getDisplayName(inLocale).replaceFirstChar(Char::uppercase)

@Composable
private fun LanguageCell(
    title: String,
    subtitle: String,
    icon: DrawableResource,
    checked: Boolean,
    onClick: () -> Unit
) {
    RowUniversal(
        onClick = onClick
    ) {
        Image(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .size(32.dp),
            painter = painterResource(icon),
            contentDescription = null
        )
        Column(modifier = Modifier.weight(1f)) {
            body_leah(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            subhead2_grey(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .width(52.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    painter = painterResource(Res.drawable.ic_checkmark_20),
                    tint = ComposeAppTheme.colors.brandDefault,
                    contentDescription = null,
                )
            }
        }
    }
}
