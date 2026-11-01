package cash.p.terminal.modules.manageaccount.backupconfirmkey

import org.jetbrains.compose.resources.getString
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.manageaccount.backupkey.BackupKeyPage
import cash.p.terminal.modules.manageaccount.recoveryphrase.RecoveryPhraseModule
import cash.p.terminal.modules.manageaccount.shamirbackup.withEmphasis
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.wallet.Account
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.Caption
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.headline2_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.delay
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.BackupConfirmKey_Error_InvalidWord
import cash.p.terminal.resources.Hud_Text_Verified
import cash.p.terminal.resources.RecoveryPhraseVerify_Description
import cash.p.terminal.resources.RecoveryPhraseVerify_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.icon_check_1_24
import cash.p.terminal.resources.word_check_correct_remaining
import cash.p.terminal.resources.word_check_position
import cash.p.terminal.resources.word_check_select_word
import cash.p.terminal.resources.word_check_title

class BackupConfirmKeyPage(val input: Account) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        RecoveryPhraseVerifyScreen(navigation, input)
    }

}

@Composable
fun RecoveryPhraseVerifyScreen(navigation: HSNavigation, account: Account) {
    val viewModel = koinViewModel<BackupConfirmKeyViewModel> { parametersOf(account) }
    WordCheckScreen(
        title = stringResource(Res.string.RecoveryPhraseVerify_Title),
        subtitle = stringResource(Res.string.RecoveryPhraseVerify_Description),
        check = viewModel.uiState.check,
        onSelect = viewModel::onSelectWord,
        onBack = navigation::navigateUpSafely,
        onVerify = { navigation.removeLastUntil(BackupKeyPage::class, true) }
    )
}

/** Confirms a completed [check] with a HUD, then calls [onVerify]. */
@Composable
internal fun WordCheckScreen(
    title: String,
    subtitle: String,
    check: WordCheckState,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit,
    onVerify: () -> Unit,
) {
    val hudMessenger = rememberHudMessenger()
    val currentOnVerify by rememberUpdatedState(onVerify)

    LaunchedEffect(check.isComplete) {
        if (check.isComplete) {
            hudMessenger.showSuccess(getString(Res.string.Hud_Text_Verified), Res.drawable.icon_check_1_24)
            delay(300)
            currentOnVerify()
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = title,
                navigationIcon = { HsBackButton(onClick = onBack) }
            )
        }
    ) {
        WordCheckContent(
            check = check,
            subtitle = subtitle,
            onSelect = onSelect,
            modifier = Modifier.padding(it)
        )
    }
}

/** Shared by every "pick the requested word" check: manual backup and Shamir parts. */
@Composable
internal fun WordCheckContent(
    check: WordCheckState,
    subtitle: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keeps the last question on screen while a completed check closes the page.
    val question = check.current ?: check.questions.lastOrNull() ?: return
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        VSpacer(16.dp)
        headline2_leah(text = stringResource(Res.string.word_check_title), textAlign = TextAlign.Center)
        VSpacer(4.dp)
        subhead2_grey(text = subtitle, textAlign = TextAlign.Center)
        VSpacer(16.dp)
        ProgressDots(total = check.questions.size, answered = check.answered)
        VSpacer(16.dp)
        Caption(text = stringResource(Res.string.word_check_select_word), color = ComposeAppTheme.colors.textPrimary)
        VSpacer(4.dp)
        WordPosition(question.position)
        VSpacer(16.dp)
        WordGrid(
            words = question.options.mapIndexed { index, word -> RecoveryPhraseModule.WordNumbered(word, index + 1) },
            modifier = Modifier.padding(horizontal = 12.dp),
            markedIndex = check.wrongOptionIndex,
            onClick = onSelect
        )
        VSpacer(8.dp)
        WordCheckCaption(check)
        VSpacer(16.dp)
    }
}

@Composable
private fun ProgressDots(total: Int, answered: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { index ->
            val color = when {
                index < answered -> ComposeAppTheme.colors.statusSuccess
                index == answered -> ComposeAppTheme.colors.brandDefault
                else -> ComposeAppTheme.colors.textSecondary
            }
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun WordPosition(position: Int) {
    Text(
        text = stringResource(Res.string.word_check_position, position)
            .withEmphasis(position.toString(), SpanStyle(color = ComposeAppTheme.colors.brandDefault)),
        style = ComposeAppTheme.typography.body,
        color = ComposeAppTheme.colors.textPrimary
    )
}

@Composable
private fun WordCheckCaption(check: WordCheckState) {
    when {
        check.wrongOptionIndex != null -> Caption(
            text = stringResource(Res.string.BackupConfirmKey_Error_InvalidWord),
            color = ComposeAppTheme.colors.statusError,
            textAlign = TextAlign.Center
        )

        check.answered > 0 && !check.isComplete -> Caption(
            text = pluralStringResource(Res.plurals.word_check_correct_remaining, check.remaining, check.remaining),
            color = ComposeAppTheme.colors.statusSuccess,
            textAlign = TextAlign.Center
        )
    }
}

/** Two columns filled top to bottom: 1 2 3 on the left, 4 5 6 on the right. */
@Composable
internal fun WordGrid(
    words: List<RecoveryPhraseModule.WordNumbered>,
    modifier: Modifier = Modifier,
    markedIndex: Int? = null,
    onClick: ((Int) -> Unit)? = null,
    cellColor: Color = ComposeAppTheme.colors.surfaceElevated,
) {
    val rows = (words.size + 1) / 2
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 until rows, rows until words.size).forEach { column ->
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                column.forEach { index ->
                    WordCell(
                        word = words[index],
                        marked = index == markedIndex,
                        color = cellColor,
                        onClick = onClick?.let { { it(index) } }
                    )
                }
            }
        }
    }
}

@Composable
private fun WordCell(word: RecoveryPhraseModule.WordNumbered, marked: Boolean, color: Color, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(8.dp)
    val border = if (marked) Modifier.border(1.dp, ComposeAppTheme.colors.statusError, shape) else Modifier
    val click = onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(shape)
            .background(color)
            .then(border)
            .then(click)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = word.number.toString(),
            style = ComposeAppTheme.typography.subhead2,
            color = ComposeAppTheme.colors.textDisabled
        )
        HSpacer(8.dp)
        Text(
            text = word.word,
            style = ComposeAppTheme.typography.body,
            color = ComposeAppTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val previewQuestion = WordQuestion(
    position = 9,
    word = "foot",
    options = listOf("medal", "obvious", "material", "bid", "foot", "roast")
)

@Composable
private fun WordCheckPreview(check: WordCheckState) {
    ComposeAppTheme {
        WordCheckContent(
            check = check,
            subtitle = stringResource(Res.string.RecoveryPhraseVerify_Description),
            onSelect = {},
            modifier = Modifier.background(ComposeAppTheme.colors.backgroundBase)
        )
    }
}

@Preview
@Composable
private fun WordCheckContentFirstWordPreview() {
    WordCheckPreview(WordCheckState(questions = List(3) { previewQuestion }))
}

@Preview
@Composable
private fun WordCheckContentCorrectPreview() {
    WordCheckPreview(WordCheckState(questions = List(3) { previewQuestion }, answered = 1))
}

@Preview
@Composable
private fun WordCheckContentWrongWordPreview() {
    WordCheckPreview(WordCheckState(questions = List(3) { previewQuestion }, answered = 1, wrongOptionIndex = 2))
}
