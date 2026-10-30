package cash.p.terminal.modules.settings.about

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.core.providers.SharedAppConfig
import cash.p.terminal.modules.syncerror.rememberMailComposer
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SettingsContact_Title
import cash.p.terminal.resources.Settings_Contact_ViaEmail
import cash.p.terminal.resources.Settings_Contact_ViaTelegram
import cash.p.terminal.resources.ic_mail_24
import cash.p.terminal.resources.telegram_link
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

class ContactOptionsSheet(val input: Input?) : HSBottomSheet() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ContactOptionsScreen(
            input = input ?: defaultInput(),
            onCloseClick = navigation::navigateUpSafely
        )
    }

    @Composable
    private fun defaultInput() = Input(
        title = Res.string.SettingsContact_Title,
        email = SharedAppConfig.reportEmail,
        telegramUrl = stringResource(Res.string.telegram_link)
    )

    data class Input(
        val title: StringResource,
        val email: String,
        val telegramUrl: String
    )
}

@Composable
private fun ContactOptionsScreen(
    input: ContactOptionsSheet.Input,
    onCloseClick: () -> Unit
) {
    val urlOpener = LocalUrlOpener.current
    val sendMail = rememberMailComposer()
    val clipboard = koinInject<IClipboardManager>()
    ComposeAppTheme {
        BottomSheetHeader(
            iconPainter = painterResource(Res.drawable.ic_mail_24),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.brandDefault),
            title = stringResource(input.title),
            onCloseClick = onCloseClick
        ) {
            VSpacer(24.dp)
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(Res.string.Settings_Contact_ViaEmail),
                onClick = {
                    if (!sendMail(input.email, "")) clipboard.copyText(input.email)
                }
            )
            VSpacer(12.dp)
            ButtonPrimaryDefault(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(Res.string.Settings_Contact_ViaTelegram),
                onClick = { urlOpener.open(input.telegramUrl) }
            )
            VSpacer(24.dp)
        }
    }
}
