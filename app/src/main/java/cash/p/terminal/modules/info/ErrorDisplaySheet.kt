package cash.p.terminal.modules.info

import android.os.Parcelable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.parcelize.Parcelize

class ErrorDisplaySheet(val input: Input) : HSBottomSheet() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ErrorDisplayScreen(
            title = input.title,
            errorText = input.text,
            onClose = { navigation.navigateUpSafely() }
        )
    }

    @Parcelize
    data class Input(val title: String, val text: String) : Parcelable
}

@Composable
private fun ErrorDisplayScreen(
    title: String,
    errorText: String,
    onClose: () -> Unit
) {
    ComposeAppTheme {
        BottomSheetHeader(
            iconPainter = painterResource(R.drawable.icon_24_warning_2),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.lucian),
            title = title,
            onCloseClick = onClose
        ) {
            VSpacer(12.dp)
            TextImportantError(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = errorText
            )
            VSpacer(8.dp)
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                title = stringResource(R.string.Button_Ok),
                onClick = onClose
            )
            VSpacer(8.dp)
        }
    }
}
