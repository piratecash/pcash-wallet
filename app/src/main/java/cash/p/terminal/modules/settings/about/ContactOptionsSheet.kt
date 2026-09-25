package cash.p.terminal.modules.settings.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui.helpers.LinkHelper
import cash.p.terminal.ui.helpers.TextHelper
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.parcelize.Parcelize

class ContactOptionsSheet(val input: Input?) : HSBottomSheet() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ContactOptionsScreen(
            input = input ?: defaultInput(),
            onCloseClick = navigation::navigateUpSafely
        )
    }

    private fun defaultInput() = Input(
        titleRes = R.string.SettingsContact_Title,
        email = AppConfigProvider.reportEmail,
        telegramUrl = Translator.getString(R.string.telegram_link)
    )

    @Parcelize
    data class Input(
        val titleRes: Int,
        val email: String,
        val telegramUrl: String
    ) : Parcelable
}

@Composable
private fun ContactOptionsScreen(
    input: ContactOptionsSheet.Input,
    onCloseClick: () -> Unit
) {
    val context = LocalContext.current
    ComposeAppTheme {
        BottomSheetHeader(
            iconPainter = painterResource(R.drawable.ic_mail_24),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.jacob),
            title = stringResource(input.titleRes),
            onCloseClick = onCloseClick
        ) {
            VSpacer(24.dp)
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(R.string.Settings_Contact_ViaEmail),
                onClick = {
                    sendEmail(input.email, context)
                }
            )
            VSpacer(12.dp)
            ButtonPrimaryDefault(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                title = stringResource(R.string.Settings_Contact_ViaTelegram),
                onClick = {
                    LinkHelper.openLinkInAppBrowser(context, input.telegramUrl)
                }
            )
            VSpacer(24.dp)
        }
    }
}

private fun sendEmail(recipient: String, context: Context) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
    }

    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        TextHelper.copyText(recipient)
    }
}
