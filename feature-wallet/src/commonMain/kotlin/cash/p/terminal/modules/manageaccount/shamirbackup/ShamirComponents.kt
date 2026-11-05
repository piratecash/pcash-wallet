package cash.p.terminal.modules.manageaccount.shamirbackup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Info_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_checkmark_20
import cash.p.terminal.resources.ic_checkmark_24
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.resources.ic_info_24
import cash.p.terminal.resources.shamir_backup_title
import cash.p.terminal.resources.shamir_info_description
import cash.p.terminal.resources.shamir_scheme_fraction
import cash.p.terminal.resources.shamir_step
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonsGroupWithShade
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.InfoBottomSheet
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal const val SHAMIR_STEP_COUNT = 3

/** App bar, scrolling [content] and the [buttons] pinned under it, shared by every step of the flow. */
@Composable
internal fun ShamirScaffold(
    title: String,
    onBack: () -> Unit,
    buttons: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        InfoBottomSheet(
            title = stringResource(Res.string.shamir_backup_title),
            text = stringResource(Res.string.shamir_info_description),
            onDismiss = { showInfo = false }
        )
    }
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = title,
                navigationIcon = { HsBackButton(onClick = onBack) },
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Info_Title),
                        icon = Res.drawable.ic_info_24,
                        onClick = { showInfo = true }
                    )
                )
            )
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                content = content
            )
            ButtonsGroupWithShade {
                Column(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = buttons
                )
            }
        }
    }
}

@Composable
internal fun ShamirHeroIcon(icon: DrawableResource) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .background(ComposeAppTheme.colors.surfacePrimary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            modifier = Modifier.size(48.dp),
            painter = painterResource(icon),
            contentDescription = null,
            tint = ComposeAppTheme.colors.iconSecondary
        )
    }
}

@Composable
internal fun ShamirPartRow(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ComposeAppTheme.colors.surfaceElevated, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        subhead1_leah(modifier = Modifier.weight(1f), text = title)
        trailing()
    }
}

@Composable
internal fun ShamirCheckIcon() {
    Icon(
        modifier = Modifier.size(24.dp),
        painter = painterResource(Res.drawable.ic_checkmark_24),
        contentDescription = null,
        tint = ComposeAppTheme.colors.statusSuccess
    )
}

@Composable
internal fun ShamirStepIndicator(step: Int, title: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(SHAMIR_STEP_COUNT) { index ->
                val active = index < step
                val color = if (active) ComposeAppTheme.colors.brandDefault else ComposeAppTheme.colors.textDisabled
                Box(
                    Modifier
                        .size(width = 32.dp, height = 2.dp)
                        .background(color, RoundedCornerShape(8.dp))
                )
            }
        }
        VSpacer(8.dp)
        subhead2_grey(
            text = stringResource(Res.string.shamir_step, step, SHAMIR_STEP_COUNT, title),
            textAlign = TextAlign.Center
        )
    }
}

/** A "do" row gets a green check, a "don't" row a red cross. */
internal class ShamirChecklistItem(val text: String, val positive: Boolean = true)

@Composable
internal fun ShamirChecklist(items: List<ShamirChecklistItem>) {
    CellUniversalLawrenceSection(
        items.map { item ->
            @Composable {
                RowUniversal(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalPadding = 16.dp,
                    verticalAlignment = Alignment.Top
                ) {
                    ChecklistIcon(item.positive)
                    HSpacer(16.dp)
                    subhead2_leah(item.text)
                }
            }
        }
    )
}

@Composable
private fun ChecklistIcon(positive: Boolean) {
    Icon(
        modifier = Modifier.size(20.dp),
        painter = painterResource(if (positive) Res.drawable.ic_checkmark_20 else Res.drawable.ic_close_24),
        contentDescription = null,
        tint = if (positive) ComposeAppTheme.colors.statusSuccess else ComposeAppTheme.colors.statusError
    )
}

/** "T of N" as one unbreakable fragment, to be emphasised inside a sentence. */
@Composable
internal fun schemeFraction(scheme: ShamirScheme): String =
    stringResource(Res.string.shamir_scheme_fraction, scheme.threshold, scheme.total)

internal fun String.withEmphasis(fragment: String, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(this@withEmphasis)
    val start = this@withEmphasis.indexOf(fragment)
    if (start >= 0) addStyle(style, start, start + fragment.length)
}

internal val boldSpan = SpanStyle(fontWeight = FontWeight.Bold)
