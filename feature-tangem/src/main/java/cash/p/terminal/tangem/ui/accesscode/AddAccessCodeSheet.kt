package cash.p.terminal.tangem.ui.accesscode

import android.os.Parcelable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.tangem.R
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.FormsInputPassword
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel

class AddAccessCodeSheet : HSBottomSheet() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<AddAccessCodeViewModel>()
        ComposeAppTheme {
            val uiState = viewModel.uiState.value
            AddAccessCodeScreen(
                uiState = uiState,
                onChangeCode = viewModel::onChangeCode,
                onChangeCodeConfirmation = viewModel::onChangeCodeConfirmation,
                onConfirmClick = {
                    navigation.setResult(this, Result(viewModel.code))
                    navigation.navigateUpSafely()
                },
                onCloseClick = {
                    navigation.navigateUpSafely()
                }
            )
        }
    }

    @Parcelize
    internal data class Result(val code: String) : Parcelable
}

@Composable
private fun AddAccessCodeScreen(
    uiState: AddAccessCodeUIState,
    onChangeCode: (String) -> Unit,
    onChangeCodeConfirmation: (String) -> Unit,
    onConfirmClick: () -> Unit,
    onCloseClick: () -> Unit
) {
    BottomSheetHeader(
        iconPainter = painterResource(R.drawable.ic_attention_24),
        iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
        title = stringResource(R.string.access_code),
        onCloseClick = onCloseClick
    ) {

        InfoText(text = stringResource(R.string.onboarding_access_code_hint))
        VSpacer(24.dp)
        FormsInputPassword(
            modifier = Modifier.padding(horizontal = 16.dp),
            hint = stringResource(R.string.access_code),
            state = uiState.dataState,
            onValueChange = onChangeCode,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next
            ),
        )
        VSpacer(16.dp)
        FormsInputPassword(
            modifier = Modifier.padding(horizontal = 16.dp),
            hint = stringResource(R.string.repeat_access_code),
            state = uiState.dataStateConfirmation,
            onValueChange = onChangeCodeConfirmation,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        VSpacer(32.dp)
        TextImportantWarning(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = stringResource(R.string.onboarding_access_code_intro_description)
        )
        VSpacer(32.dp)

        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp),
            title = stringResource(R.string.common_continue),
            enabled = uiState.confirmEnabled,
            onClick = onConfirmClick
        )
        VSpacer(32.dp)
    }
}

@Composable
@Preview(showBackground = true)
private fun AddAccessCodeScreenPreview() {
    ComposeAppTheme {
        AddAccessCodeScreen(
            uiState = AddAccessCodeUIState(),
            onChangeCode = {},
            onChangeCodeConfirmation = {},
            onConfirmClick = {},
            onCloseClick = {}
        )
    }
}
