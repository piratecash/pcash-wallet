package cash.p.terminal.modules.settings.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.modules.releasenotes.ReleaseNotesPage
import cash.p.terminal.modules.settings.appstatus.AppStatusPage
import cash.p.terminal.modules.settings.main.HsSettingCell
import cash.p.terminal.modules.settings.privacy.PrivacyPage
import cash.p.terminal.modules.settings.terms.TermsPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui.helpers.LinkHelper
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead1_jacob
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

class AboutPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        AboutScreen(
            navigation = navigation,
            onBackPress = navigation::navigateUpSafely
        )
    }

}

@Composable
private fun AboutScreen(
    navigation: HSNavigation,
    onBackPress: () -> Unit,
    aboutViewModel: AboutViewModel = viewModel(factory = AboutModule.Factory()),
) {
    Surface(color = ComposeAppTheme.colors.tyler) {
        Column {
            AppBar(
                title = stringResource(R.string.SettingsAboutApp_Title),
                navigationIcon = {
                    HsBackButton(onClick = onBackPress)
                }
            )

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(12.dp))
                SettingSections(aboutViewModel, navigation)
                Spacer(Modifier.height(36.dp))
            }
        }
    }
}

@Composable
private fun SettingSections(
    viewModel: AboutViewModel,
    navigation: HSNavigation
) {

    val context = LocalContext.current
    val termsShowAlert = viewModel.termsShowAlert

    CellUniversalLawrenceSection(
        listOf {
            HsSettingCell(
                title = R.string.SettingsAboutApp_AppVersion,
                icon = R.drawable.ic_info_20,
                value = viewModel.appVersion,
                onClick = {
                    navigation.slideFromRight(ReleaseNotesPage(ReleaseNotesPage.Input(false)))
                }
            )
        }
    )

    Spacer(Modifier.height(32.dp))

    CellUniversalLawrenceSection(
        listOf({
            HsSettingCell(
                R.string.Settings_AppStatus,
                R.drawable.ic_app_status,
                onClick = {
                    navigation.slideFromRight(AppStatusPage())
                }
            )
        }, {
            HsSettingCell(
                R.string.Settings_Terms,
                R.drawable.ic_terms_20,
                showAlert = termsShowAlert,
                onClick = {
                    navigation.slideFromBottom(TermsPage())
                }
            )
        }, {
            HsSettingCell(
                R.string.Settings_Privacy,
                R.drawable.ic_user_20,
                onClick = {
                    navigation.slideFromRight(PrivacyPage())
                }
            )
        })
    )

    Spacer(Modifier.height(32.dp))

    CellUniversalLawrenceSection(
        listOf({
            HsSettingCell(
                R.string.SettingsAboutApp_Github,
                R.drawable.ic_github_20,
                onClick = {
                    LinkHelper.openLinkInAppBrowser(context, viewModel.githubLink)
                }
            )
        }, {
            HsSettingCell(
                R.string.SettingsAboutApp_Site,
                R.drawable.ic_globe,
                onClick = {
                    LinkHelper.openLinkInAppBrowser(context, viewModel.appWebPageLink)
                }
            )
        })
    )

    Spacer(Modifier.height(32.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        subhead1_jacob(text = stringResource(id = R.string.Settings_JoinUs).uppercase())
    }
    CellUniversalLawrenceSection(
        listOf({
            HsSettingCell(
                R.string.Settings_Telegram,
                R.drawable.ic_telegram_filled_24,
                onClick = {
                    LinkHelper.openLinkInAppBrowser(context, AppConfigProvider.appTelegramLink)
                }
            )
        }, {
            HsSettingCell(
                R.string.Settings_Twitter,
                R.drawable.ic_twitter_filled_24,
                onClick = {
                    LinkHelper.openLinkInAppBrowser(context, AppConfigProvider.appTwitterLink)
                }
            )
        })
    )
    InfoText(
        text = stringResource(R.string.Settings_JoinUs_Description),
    )

    VSpacer(32.dp)
}
