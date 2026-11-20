package cash.p.terminal.modules.restoreaccount.restoreshamir

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.manageaccount.shamirbackup.ShamirCheckIcon
import cash.p.terminal.modules.manageaccount.shamirbackup.ShamirHeroIcon
import cash.p.terminal.modules.manageaccount.shamirbackup.ShamirPartRow
import cash.p.terminal.modules.restoreaccount.restoremnemonic.PhraseInputField
import cash.p.terminal.modules.restoreaccount.restoremnemonic.PhraseSuggestions
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestorePassphraseSection
import cash.p.terminal.modules.restoreaccount.restoremnemonic.withSuggestion
import cash.p.terminal.resources.Button_Next
import cash.p.terminal.resources.ManageAccount_Name
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_shield_24
import cash.p.terminal.resources.shamir_backup_title
import cash.p.terminal.resources.shamir_error_mixed_sets
import cash.p.terminal.resources.shamir_error_passphrase_chars
import cash.p.terminal.resources.shamir_restore_accepted
import cash.p.terminal.resources.shamir_restore_circle_accepted
import cash.p.terminal.resources.shamir_restore_circle_required
import cash.p.terminal.resources.shamir_restore_enough
import cash.p.terminal.resources.shamir_restore_enter_first
import cash.p.terminal.resources.shamir_restore_group_progress
import cash.p.terminal.resources.shamir_restore_groups
import cash.p.terminal.resources.shamir_restore_part
import cash.p.terminal.resources.shamir_restore_part_input
import cash.p.terminal.resources.shamir_restore_required
import cash.p.terminal.resources.shamir_restore_scheme_auto
import cash.p.terminal.resources.shamir_restore_title
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.components.caption_lucian
import cash.p.terminal.ui_compose.components.headline1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.components.TextImportantInfo
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal class ShamirWalletActions(
    val onEnterName: (String) -> Unit,
    val onTogglePassphrase: (Boolean) -> Unit,
    val onEnterPassphrase: (String) -> Unit,
)

@Composable
internal fun RestoreShamirScreen(
    uiState: RestoreShamirUiState,
    onEnterShare: (String, Int) -> Unit,
    onTypeShare: () -> Unit,
    onContinue: () -> Unit,
    onScanClick: () -> Unit,
    walletActions: ShamirWalletActions,
    onBackClick: () -> Unit,
) {
    var textState by remember { mutableStateOf(TextFieldValue(uiState.input, TextRange(uiState.input.length))) }
    // The view model clears or replaces the input after a share is accepted or scanned.
    LaunchedEffect(uiState.input) {
        if (uiState.input != textState.text) {
            textState = TextFieldValue(uiState.input, TextRange(uiState.input.length))
        }
    }
    var inputFocused by remember { mutableStateOf(false) }
    val onTextChange: (TextFieldValue) -> Unit = {
        textState = it
        onEnterShare(it.text, it.selection.max)
    }
    val primaryEnabled = if (uiState.isComplete) {
        !uiState.restoring && uiState.passphraseError == null
    } else {
        uiState.input.isNotBlank()
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.shamir_restore_title),
                navigationIcon = { HsBackButton(onClick = onBackClick) },
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Button_Next),
                        enabled = primaryEnabled,
                        onClick = onContinue
                    )
                )
            )
        }
    ) { paddingValues ->
        Column(Modifier.padding(top = paddingValues.calculateTopPadding())) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                if (uiState.isComplete) {
                    CompleteContent(uiState, walletActions)
                } else {
                    InputContent(uiState, textState, onTextChange, onTypeShare, onScanClick) { inputFocused = it }
                }
            }
            PhraseSuggestions(inputFocused && !uiState.isComplete, uiState.wordSuggestions) { wordItem, suggestion ->
                textState.withSuggestion(wordItem, suggestion)?.let(onTextChange)
            }
        }
    }
}

@Composable
private fun InputContent(
    uiState: RestoreShamirUiState,
    textState: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onTypeShare: () -> Unit,
    onScanClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
) = Column {
    VSpacer(12.dp)
    if (uiState.acceptedCount == 0) {
        ShamirHeader()
    } else {
        AcceptedParts(uiState.acceptedCount)
        VSpacer(32.dp)
        uiState.progress?.let { ProgressCard(it, uiState.isComplete) }
    }
    VSpacer(32.dp)
    ShareInputCard(
        partNumber = uiState.acceptedCount + 1,
        textState = textState,
        uiState = uiState,
        onTextChange = onTextChange,
        onTypeShare = onTypeShare,
        onScanClick = onScanClick,
        onFocusChange = onFocusChange,
    )
    uiState.inputError?.let {
        VSpacer(8.dp)
        caption_lucian(modifier = Modifier.padding(horizontal = 32.dp), text = stringResource(it))
    }
    if (uiState.acceptedCount == 0) {
        VSpacer(32.dp)
        TextImportantInfo(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(Res.string.shamir_restore_scheme_auto)
        )
    }
    VSpacer(32.dp)
}

@Composable
private fun CompleteContent(uiState: RestoreShamirUiState, actions: ShamirWalletActions) = Column {
    VSpacer(12.dp)
    uiState.progress?.let {
        ProgressCard(it, uiState.isComplete)
        VSpacer(32.dp)
    }
    AcceptedParts(uiState.acceptedCount)
    VSpacer(32.dp)
    TextImportantInfo(
        modifier = Modifier.padding(horizontal = 16.dp),
        text = stringResource(Res.string.shamir_restore_enough)
    )
    VSpacer(32.dp)
    HeaderText(stringResource(Res.string.ManageAccount_Name))
    FormsInput(
        singleLine = true,
        modifier = Modifier.padding(horizontal = 16.dp),
        enabled = !uiState.restoring,
        initial = null,
        pasteEnabled = false,
        hint = uiState.defaultAccountName,
        onValueChange = actions.onEnterName
    )
    VSpacer(32.dp)
    RestorePassphraseSection(
        enabled = uiState.passphraseEnabled,
        passphrase = uiState.passphrase,
        error = uiState.passphraseError?.let { stringResource(it) },
        onToggle = actions.onTogglePassphrase,
        onChange = actions.onEnterPassphrase,
        editable = !uiState.restoring,
    )
    VSpacer(32.dp)
}

@Composable
private fun ShamirHeader() {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ShamirHeroIcon(Res.drawable.ic_shield_24)
        VSpacer(24.dp)
        headline1_leah(stringResource(Res.string.shamir_backup_title))
        VSpacer(6.dp)
        subhead2_grey(stringResource(Res.string.shamir_restore_enter_first), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ShareInputCard(
    partNumber: Int,
    textState: TextFieldValue,
    uiState: RestoreShamirUiState,
    onTextChange: (TextFieldValue) -> Unit,
    onTypeShare: () -> Unit,
    onScanClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .background(ComposeAppTheme.colors.surfaceElevated, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        body_leah(stringResource(Res.string.shamir_restore_part_input, partNumber))
        VSpacer(12.dp)
        PhraseInputField(
            textState = textState,
            invalidWordRanges = uiState.invalidWordRanges,
            isError = uiState.inputError != null,
            onValueChange = {
                onTextChange(it)
                onTypeShare()
            },
            onTextReplace = onTextChange,
            onScanClick = onScanClick,
            onFocusChange = onFocusChange,
            backgroundColor = ComposeAppTheme.colors.surfaceElevated,
        )
    }
}

@Composable
private fun AcceptedParts(count: Int) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(count) { index ->
            ShamirPartRow(title = stringResource(Res.string.shamir_restore_part, index + 1)) { ShamirCheckIcon() }
        }
    }
}

@Composable
private fun ProgressCard(progress: ShamirProgress, isComplete: Boolean) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .background(ComposeAppTheme.colors.surfaceElevated, RoundedCornerShape(12.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (progress) {
            is ShamirProgress.SingleGroup -> SingleGroupProgress(progress, isComplete)
            is ShamirProgress.MultiGroup -> MultiGroupProgress(progress)
        }
    }
}

@Composable
private fun SingleGroupProgress(progress: ShamirProgress.SingleGroup, isComplete: Boolean) {
    if (!isComplete) {
        subhead2_leah(pluralStringResource(Res.plurals.shamir_restore_required, progress.required, progress.required))
        VSpacer(16.dp)
    }
    ThresholdCircles(required = progress.required, accepted = progress.accepted)
    if (!isComplete) {
        VSpacer(12.dp)
        caption_grey(
            text = stringResource(Res.string.shamir_restore_accepted, progress.accepted, progress.required),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MultiGroupProgress(progress: ShamirProgress.MultiGroup) {
    subhead2_grey(
        stringResource(Res.string.shamir_restore_groups, progress.completeGroups, progress.groupThreshold)
    )
    progress.groups.forEach { group ->
        VSpacer(8.dp)
        caption_grey(
            stringResource(Res.string.shamir_restore_group_progress, group.number, group.accepted, group.required)
        )
    }
}

// Even gaps between circles and card edges, as in the design; large schemes wrap onto further rows.
@Composable
private fun ThresholdCircles(required: Int, accepted: Int) {
    val columns = minOf(required, MAX_CIRCLES_PER_ROW)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in (1..required).chunked(columns)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(columns) { column ->
                    Box(modifier = Modifier.width(CIRCLE_SIZE), contentAlignment = Alignment.Center) {
                        row.getOrNull(column)?.let { ThresholdCircle(number = it, accepted = it <= accepted) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThresholdCircle(number: Int, accepted: Boolean) {
    val circle = Modifier.size(CIRCLE_SIZE)
    val accentColor = if (accepted) ComposeAppTheme.colors.statusSuccess else ComposeAppTheme.colors.brandDefault
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = if (accepted) {
                circle.background(accentColor, CircleShape)
            } else {
                circle.border(1.dp, accentColor, CircleShape)
            },
            contentAlignment = Alignment.Center
        ) {
            body_leah(number.toString())
        }
        VSpacer(8.dp)
        // The caption is wider than the circle cell; let it overflow centred instead of wrapping.
        Text(
            modifier = Modifier.wrapContentWidth(unbounded = true),
            text = stringResource(
                if (accepted) Res.string.shamir_restore_circle_accepted else Res.string.shamir_restore_circle_required
            ),
            color = accentColor,
            style = ComposeAppTheme.typography.micro,
            maxLines = 1,
        )
    }
}

@Preview
@Composable
private fun RestoreShamirScreenEmptyPreview() {
    ShamirScreenPreview(RestoreShamirUiState())
}

@Preview
@Composable
private fun RestoreShamirScreenOneOfTwoPreview() {
    ShamirScreenPreview(
        RestoreShamirUiState(acceptedCount = 1, progress = ShamirProgress.SingleGroup(required = 2, accepted = 1))
    )
}

@Preview
@Composable
private fun RestoreShamirScreenLargeSchemePreview() {
    ShamirScreenPreview(
        RestoreShamirUiState(acceptedCount = 3, progress = ShamirProgress.SingleGroup(required = 6, accepted = 3))
    )
}

@Preview
@Composable
private fun RestoreShamirScreenErrorPreview() {
    ShamirScreenPreview(
        RestoreShamirUiState(
            input = "academic acid acrobat",
            inputError = Res.string.shamir_error_mixed_sets,
            acceptedCount = 1,
            progress = ShamirProgress.SingleGroup(required = 3, accepted = 1)
        )
    )
}

@Preview
@Composable
private fun RestoreShamirScreenMultiGroupPreview() {
    ShamirScreenPreview(
        RestoreShamirUiState(
            acceptedCount = 3,
            progress = ShamirProgress.MultiGroup(
                groupThreshold = 2,
                completeGroups = 1,
                groups = listOf(GroupProgress(1, 2, 2), GroupProgress(3, 1, 2))
            )
        )
    )
}

@Preview
@Composable
private fun RestoreShamirScreenCompletePreview() {
    ShamirScreenPreview(
        RestoreShamirUiState(
            acceptedCount = 2,
            progress = ShamirProgress.SingleGroup(required = 2, accepted = 2),
            isComplete = true,
            passphraseEnabled = true,
            passphrase = "correct horse",
        )
    )
}

@Preview
@Composable
private fun RestoreShamirScreenPassphraseErrorPreview() {
    ShamirScreenPreview(
        RestoreShamirUiState(
            acceptedCount = 2,
            progress = ShamirProgress.SingleGroup(required = 2, accepted = 2),
            isComplete = true,
            passphraseEnabled = true,
            passphrase = "пароль",
            passphraseError = Res.string.shamir_error_passphrase_chars,
        )
    )
}

@Composable
private fun ShamirScreenPreview(uiState: RestoreShamirUiState) {
    ComposeAppTheme {
        RestoreShamirScreen(
            uiState = uiState.copy(defaultAccountName = "Wallet 1"),
            onEnterShare = { _, _ -> },
            onTypeShare = {},
            onContinue = {},
            onScanClick = {},
            walletActions = ShamirWalletActions({}, {}, {}),
            onBackClick = {},
        )
    }
}

private const val MAX_CIRCLES_PER_ROW = 4
private val CIRCLE_SIZE = 40.dp
