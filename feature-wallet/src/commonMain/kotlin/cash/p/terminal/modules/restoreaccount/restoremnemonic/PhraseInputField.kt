package cash.p.terminal.modules.restoreaccount.restoremnemonic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cash.p.terminal.core.launchAfterClearingFocus
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicModule.WordItem
import cash.p.terminal.modules.restoreaccount.restoremnemonic.RestoreMnemonicModule.WordSuggestions
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Restore_PhraseHint
import cash.p.terminal.resources.Send_Button_Paste
import cash.p.terminal.resources.ic_delete_20
import cash.p.terminal.ui.compose.Keyboard
import cash.p.terminal.ui.compose.observeKeyboardState
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.ButtonSecondaryDefault
import cash.p.terminal.ui_compose.components.body_disabled
import cash.p.terminal.ui_compose.theme.ColoredTextStyle
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.ui.compose.secureKeyboardEntry

/** Recovery words input: highlights [invalidWordRanges], offers Scan/Paste while empty and Clear otherwise. */
@Composable
fun PhraseInputField(
    textState: TextFieldValue,
    invalidWordRanges: List<IntRange>,
    isError: Boolean,
    onValueChange: (TextFieldValue) -> Unit,
    onTextReplace: (TextFieldValue) -> Unit,
    onScanClick: () -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = ComposeAppTheme.colors.surfacePrimary,
    header: @Composable ColumnScope.() -> Unit = {},
) {
    val borderColor = if (isError) ComposeAppTheme.colors.statusError50 else ComposeAppTheme.colors.borderDefault
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(backgroundColor),
    ) {
        header()
        PhraseTextField(textState, invalidWordRanges, onValueChange, onFocusChange)
        PhraseInputActions(textState.text.isEmpty(), onTextReplace, onScanClick)
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun PhraseTextField(
    textState: TextFieldValue,
    invalidWordRanges: List<IntRange>,
    onValueChange: (TextFieldValue) -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    val style = SpanStyle(
        color = ComposeAppTheme.colors.statusError,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.sp
    )

    BasicTextField(
        modifier = Modifier
            .fillMaxWidth()
            .secureKeyboardEntry()
            .onFocusChanged { onFocusChange(it.isFocused) }
            .defaultMinSize(minHeight = 68.dp)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        enabled = true,
        value = textState,
        onValueChange = onValueChange,
        textStyle = ColoredTextStyle(
            color = ComposeAppTheme.colors.textPrimary,
            textStyle = ComposeAppTheme.typography.body
        ),
        maxLines = 6,
        cursorBrush = SolidColor(ComposeAppTheme.colors.brandDefault),
        visualTransformation = {
            try {
                val annotatedString = buildAnnotatedString {
                    append(it.text)

                    invalidWordRanges.forEach { range ->
                        addStyle(style = style, range.first, range.last + 1)
                    }
                }
                TransformedText(annotatedString, OffsetMapping.Identity)
            } catch (error: Throwable) {
                error.printStackTrace()
                TransformedText(it, OffsetMapping.Identity)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        decorationBox = { innerTextField ->
            if (textState.text.isEmpty()) {
                body_disabled(
                    stringResource(Res.string.Restore_PhraseHint),
                    overflow = TextOverflow.Ellipsis,
                )
            }
            innerTextField()
        },
    )
}

@Composable
private fun PhraseInputActions(
    isEmpty: Boolean,
    onTextReplace: (TextFieldValue) -> Unit,
    onScanClick: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isEmpty) {
            ButtonSecondaryCircle(
                modifier = Modifier.padding(end = 16.dp),
                icon = Res.drawable.ic_delete_20,
                onClick = { onTextReplace(TextFieldValue("", TextRange(0))) }
            )
        } else {
            ScanQrButton {
                coroutineScope.launchAfterClearingFocus(focusManager, onScanClick)
            }

            val clipboardManager = LocalClipboardManager.current
            ButtonSecondaryDefault(
                modifier = Modifier.padding(end = 16.dp),
                title = stringResource(Res.string.Send_Button_Paste),
                onClick = {
                    clipboardManager.getText()?.text?.let { textInClipboard ->
                        onTextReplace(TextFieldValue(textInClipboard, TextRange(textInClipboard.length)))
                    }
                },
            )
        }
    }
}

/** Suggestions above the keyboard while [inputFocused]; otherwise only keeps the content above the IME. */
@Composable
internal fun PhraseSuggestions(
    inputFocused: Boolean,
    wordSuggestions: WordSuggestions?,
    onSelect: (WordItem, String) -> Unit,
) {
    val keyboardState by observeKeyboardState()
    if (inputFocused && keyboardState == Keyboard.Opened) {
        SuggestionsBar(
            modifier = Modifier.imePadding(),
            wordSuggestions = wordSuggestions,
            onClick = onSelect
        )
    } else {
        Spacer(modifier = Modifier.imePadding())
    }
}

/** Replaces [wordItem] with [suggestion]; null when the suggestion is stale for the current text. */
internal fun TextFieldValue.withSuggestion(wordItem: WordItem, suggestion: String): TextFieldValue? {
    if (wordItem.range.last > text.length) return null

    val cursorIndex = wordItem.range.first + suggestion.length + 1
    var newText = text.replaceRange(wordItem.range, suggestion)
    if (newText.length < cursorIndex) {
        newText = "$newText "
    }
    return TextFieldValue(text = newText, selection = TextRange(cursorIndex))
}

@Preview
@Composable
private fun PhraseInputFieldEmptyPreview() {
    PhraseInputFieldPreview(TextFieldValue(""), emptyList())
}

@Preview
@Composable
private fun PhraseInputFieldInvalidWordPreview() {
    val text = "abandon ability abel about"
    PhraseInputFieldPreview(TextFieldValue(text, TextRange(text.length)), listOf(16..19))
}

@Composable
private fun PhraseInputFieldPreview(textState: TextFieldValue, invalidWordRanges: List<IntRange>) {
    ComposeAppTheme {
        PhraseInputField(
            textState = textState,
            invalidWordRanges = invalidWordRanges,
            isError = invalidWordRanges.isNotEmpty(),
            onValueChange = {},
            onTextReplace = {},
            onScanClick = {},
            onFocusChange = {},
        )
    }
}
