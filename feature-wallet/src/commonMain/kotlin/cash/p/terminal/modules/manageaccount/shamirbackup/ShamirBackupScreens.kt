package cash.p.terminal.modules.manageaccount.shamirbackup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Button_Back
import cash.p.terminal.resources.Button_Next
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_down_arrow_20
import cash.p.terminal.resources.ic_attention_24
import cash.p.terminal.resources.shamir_backup_title
import cash.p.terminal.resources.shamir_create
import cash.p.terminal.resources.shamir_intro_any
import cash.p.terminal.resources.shamir_intro_description
import cash.p.terminal.resources.shamir_intro_holder_friend
import cash.p.terminal.resources.shamir_intro_holder_home
import cash.p.terminal.resources.shamir_intro_holder_you
import cash.p.terminal.resources.shamir_intro_lost
import cash.p.terminal.resources.shamir_intro_people
import cash.p.terminal.resources.shamir_intro_stolen
import cash.p.terminal.resources.shamir_intro_title
import cash.p.terminal.resources.shamir_scheme_any_parts
import cash.p.terminal.resources.shamir_scheme_can_lose
import cash.p.terminal.resources.shamir_scheme_none_lose
import cash.p.terminal.resources.shamir_scheme_presets
import cash.p.terminal.resources.shamir_scheme_threshold
import cash.p.terminal.resources.shamir_scheme_threshold_header
import cash.p.terminal.resources.shamir_scheme_title
import cash.p.terminal.resources.shamir_scheme_total
import cash.p.terminal.resources.shamir_scheme_total_header
import cash.p.terminal.resources.shamir_show_shares
import cash.p.terminal.resources.shamir_step_scheme
import cash.p.terminal.resources.shamir_step_warnings
import cash.p.terminal.resources.shamir_warning_description
import cash.p.terminal.resources.shamir_warning_different_places
import cash.p.terminal.resources.shamir_warning_no_photos
import cash.p.terminal.resources.shamir_warning_not_shown_again
import cash.p.terminal.resources.shamir_warning_old_sets
import cash.p.terminal.resources.shamir_warning_one_place
import cash.p.terminal.resources.shamir_warning_passphrase
import cash.p.terminal.resources.shamir_warning_separate_paper
import cash.p.terminal.resources.shamir_warning_title
import cash.p.terminal.ui_compose.components.AppSelectorDialog
import cash.p.terminal.ui_compose.components.AppSelectorItem
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.headline2_leah
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.components.TextImportantInfo
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val introScheme = ShamirScheme(threshold = 2, total = 3)

@Composable
internal fun ShamirIntroScreen(onCreate: () -> Unit, onBack: () -> Unit) {
    ShamirScaffold(
        title = stringResource(Res.string.shamir_backup_title),
        onBack = onBack,
        buttons = {
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.shamir_create),
                onClick = onCreate
            )
        }
    ) {
        VSpacer(16.dp)
        ShamirTitle(
            title = stringResource(Res.string.shamir_intro_title),
            subtitle = stringResource(Res.string.shamir_intro_description)
        )
        VSpacer(32.dp)
        IntroSchemeCard()
        VSpacer(32.dp)
        ShamirChecklist(
            listOf(
                ShamirChecklistItem(stringResource(Res.string.shamir_intro_stolen)),
                ShamirChecklistItem(stringResource(Res.string.shamir_intro_lost)),
                ShamirChecklistItem(stringResource(Res.string.shamir_intro_people)),
            )
        )
        VSpacer(32.dp)
    }
}

@Composable
private fun IntroSchemeCard() {
    ShamirCard(spacing = 20.dp) {
        // Equal cells sized by the widest caption keep the circles evenly spaced in every locale.
        Row(modifier = Modifier.width(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            val cell = Modifier.weight(1f)
            HolderCircle(1, stringResource(Res.string.shamir_intro_holder_you), highlighted = true, modifier = cell)
            HolderCircle(2, stringResource(Res.string.shamir_intro_holder_home), highlighted = true, modifier = cell)
            HolderCircle(3, stringResource(Res.string.shamir_intro_holder_friend), highlighted = false, modifier = cell)
        }
        val fraction = schemeFraction(introScheme)
        subhead2_grey(
            text = stringResource(Res.string.shamir_intro_any, fraction).withEmphasis(
                fragment = fraction,
                style = boldSpan.merge(SpanStyle(color = ComposeAppTheme.colors.statusSuccess))
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HolderCircle(number: Int, caption: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    val color = if (highlighted) ComposeAppTheme.colors.brandDefault else ComposeAppTheme.colors.textSecondary
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        NumberCircle(number = number, size = 40.dp, color = color)
        VSpacer(8.dp)
        Text(text = caption, style = ComposeAppTheme.typography.micro, color = color, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NumberCircle(number: Int, size: Dp, color: Color) {
    Box(
        modifier = Modifier
            .size(size)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            style = ComposeAppTheme.typography.body,
            color = ComposeAppTheme.colors.buttonPrimaryBrandContent
        )
    }
}

@Composable
internal fun ShamirSchemeScreen(
    scheme: ShamirScheme,
    actions: ShamirSchemeActions,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    ShamirScaffold(
        title = stringResource(Res.string.shamir_backup_title),
        onBack = onBack,
        buttons = {
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.Button_Next),
                onClick = onNext
            )
        }
    ) {
        ShamirStepIndicator(step = 1, title = stringResource(Res.string.shamir_step_scheme))
        VSpacer(8.dp)
        SchemeSelector(
            header = stringResource(Res.string.shamir_scheme_total_header),
            title = stringResource(Res.string.shamir_scheme_total),
            value = scheme.total,
            options = ShamirScheme.TOTAL_RANGE,
            onSelect = actions.onSelectTotal
        )
        VSpacer(8.dp)
        SchemeSelector(
            header = stringResource(Res.string.shamir_scheme_threshold_header),
            title = stringResource(Res.string.shamir_scheme_threshold),
            value = scheme.threshold,
            options = scheme.thresholdOptions,
            onSelect = actions.onSelectThreshold
        )
        VSpacer(24.dp)
        SchemePreviewCard(scheme)
        VSpacer(24.dp)
        HeaderText(stringResource(Res.string.shamir_scheme_presets))
        VSpacer(8.dp)
        Presets(selected = scheme, onSelect = actions.onSelectPreset)
        VSpacer(32.dp)
    }
}

internal class ShamirSchemeActions(
    val onSelectTotal: (Int) -> Unit,
    val onSelectThreshold: (Int) -> Unit,
    val onSelectPreset: (ShamirScheme) -> Unit,
)

@Composable
private fun SchemeSelector(header: String, title: String, value: Int, options: IntRange, onSelect: (Int) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    HeaderText(header)
    CellUniversalLawrenceSection {
        RowUniversal(modifier = Modifier.padding(horizontal = 16.dp), onClick = { showDialog = true }) {
            body_leah(modifier = Modifier.weight(1f), text = title)
            subhead1_grey(text = value.toString())
            Icon(
                modifier = Modifier.padding(start = 8.dp),
                painter = painterResource(Res.drawable.ic_down_arrow_20),
                contentDescription = null,
                tint = ComposeAppTheme.colors.iconSecondary
            )
        }
    }
    if (showDialog) {
        AppSelectorDialog(
            title = header,
            items = options.map { AppSelectorItem(it.toString(), it == value, it) },
            onDismiss = { showDialog = false },
            onSelect = onSelect
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SchemePreviewCard(scheme: ShamirScheme) {
    ShamirCard(spacing = 16.dp) {
        subhead2_leah(stringResource(Res.string.shamir_scheme_title, schemeFraction(scheme)))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (number in 1..scheme.total) {
                val color = if (number <= scheme.threshold) {
                    ComposeAppTheme.colors.statusSuccess
                } else {
                    ComposeAppTheme.colors.textSecondary
                }
                NumberCircle(number = number, size = 32.dp, color = color)
            }
        }
        Text(
            text = schemeSummary(scheme),
            style = ComposeAppTheme.typography.subhead2,
            color = ComposeAppTheme.colors.statusSuccess,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun schemeSummary(scheme: ShamirScheme): String {
    val spare = scheme.total - scheme.threshold
    val anyParts = pluralStringResource(Res.plurals.shamir_scheme_any_parts, scheme.threshold, scheme.threshold)
    val loss = if (spare > 0) {
        stringResource(Res.string.shamir_scheme_can_lose, spare)
    } else {
        stringResource(Res.string.shamir_scheme_none_lose)
    }
    return "$anyParts · $loss"
}

@Composable
private fun Presets(selected: ShamirScheme, onSelect: (ShamirScheme) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally)
    ) {
        ShamirScheme.PRESETS.forEach { preset ->
            PresetChip(text = schemeFraction(preset), selected = preset == selected) { onSelect(preset) }
        }
    }
}

@Composable
private fun PresetChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val accent = if (selected) ComposeAppTheme.colors.brandDefault else ComposeAppTheme.colors.textSecondary
    Box(
        modifier = Modifier
            .clip(shape)
            .background(ComposeAppTheme.colors.surfaceElevated)
            .then(if (selected) Modifier.border(1.dp, accent, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = text, style = ComposeAppTheme.typography.subhead2, color = accent)
    }
}

@Composable
internal fun ShamirWarningsScreen(
    scheme: ShamirScheme,
    showPassphraseWarning: Boolean,
    onShowShares: () -> Unit,
    onBack: () -> Unit,
) {
    ShamirScaffold(
        title = stringResource(Res.string.shamir_backup_title),
        onBack = onBack,
        buttons = {
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.shamir_show_shares),
                onClick = onShowShares
            )
            ButtonPrimaryTransparent(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.Button_Back),
                onClick = onBack
            )
        }
    ) {
        ShamirStepIndicator(step = 2, title = stringResource(Res.string.shamir_step_warnings))
        VSpacer(8.dp)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ShamirHeroIcon(Res.drawable.ic_attention_24)
        }
        VSpacer(24.dp)
        ShamirTitle(
            title = stringResource(Res.string.shamir_warning_title),
            subtitle = pluralStringResource(Res.plurals.shamir_warning_description, scheme.total, scheme.total),
            spacing = 6.dp
        )
        VSpacer(16.dp)
        ShamirChecklist(
            listOf(
                ShamirChecklistItem(stringResource(Res.string.shamir_warning_one_place), positive = false),
                ShamirChecklistItem(stringResource(Res.string.shamir_warning_no_photos), positive = false),
                ShamirChecklistItem(stringResource(Res.string.shamir_warning_separate_paper)),
                ShamirChecklistItem(stringResource(Res.string.shamir_warning_different_places)),
            )
        )
        VSpacer(16.dp)
        TextImportantInfo(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(Res.string.shamir_warning_old_sets)
        )
        if (showPassphraseWarning) {
            VSpacer(16.dp)
            TextImportantWarning(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = stringResource(Res.string.shamir_warning_passphrase)
            )
        }
        VSpacer(16.dp)
        TextImportantWarning(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(Res.string.shamir_warning_not_shown_again)
        )
        VSpacer(32.dp)
    }
}

@Composable
private fun ShamirTitle(title: String, subtitle: String, spacing: Dp = 4.dp) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        headline2_leah(text = title, textAlign = TextAlign.Center)
        Spacer(Modifier.size(spacing))
        subhead2_grey(text = subtitle, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ShamirCard(spacing: Dp, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .background(ComposeAppTheme.colors.surfaceElevated, RoundedCornerShape(12.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        content()
    }
}

@Preview
@Composable
private fun ShamirIntroScreenPreview() {
    ComposeAppTheme {
        ShamirIntroScreen(onCreate = {}, onBack = {})
    }
}

@Composable
private fun SchemePreview(scheme: ShamirScheme) {
    ComposeAppTheme {
        ShamirSchemeScreen(scheme = scheme, actions = ShamirSchemeActions({}, {}, {}), onNext = {}, onBack = {})
    }
}

@Preview
@Composable
private fun ShamirSchemeScreenPresetPreview() {
    SchemePreview(ShamirScheme(threshold = 2, total = 3))
}

@Preview
@Composable
private fun ShamirSchemeScreenLargePreview() {
    SchemePreview(ShamirScheme(threshold = 16, total = 16))
}

@Preview
@Composable
private fun ShamirWarningsScreenPreview() {
    ComposeAppTheme {
        ShamirWarningsScreen(scheme = introScheme, showPassphraseWarning = false, onShowShares = {}, onBack = {})
    }
}

@Preview
@Composable
private fun ShamirWarningsScreenPassphrasePreview() {
    ComposeAppTheme {
        ShamirWarningsScreen(
            scheme = ShamirScheme(threshold = 3, total = 5),
            showPassphraseWarning = true,
            onShowShares = {},
            onBack = {}
        )
    }
}
