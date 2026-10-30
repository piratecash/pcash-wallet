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
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.providers.SharedAppConfig
import cash.p.terminal.feature.wallet.WalletPlatformPages
import cash.p.terminal.modules.releasenotes.ReleaseNotesPage
import cash.p.terminal.modules.settings.appstatus.AppStatusPage
import cash.p.terminal.modules.settings.main.SettingsCell
import cash.p.terminal.modules.settings.main.settingsAlertPainter
import cash.p.terminal.modules.settings.terms.TermsPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SettingsAboutApp_AppVersion
import cash.p.terminal.resources.SettingsAboutApp_Github
import cash.p.terminal.resources.SettingsAboutApp_Site
import cash.p.terminal.resources.SettingsAboutApp_Title
import cash.p.terminal.resources.Settings_AppStatus
import cash.p.terminal.resources.Settings_JoinUs
import cash.p.terminal.resources.Settings_JoinUs_Description
import cash.p.terminal.resources.Settings_Privacy
import cash.p.terminal.resources.Settings_Telegram
import cash.p.terminal.resources.Settings_Terms
import cash.p.terminal.resources.Settings_Twitter
import cash.p.terminal.resources.ic_app_status
import cash.p.terminal.resources.ic_github_20
import cash.p.terminal.resources.ic_globe
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.resources.ic_telegram_filled_24
import cash.p.terminal.resources.ic_terms_20
import cash.p.terminal.resources.ic_twitter_filled_24
import cash.p.terminal.resources.ic_user_20
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead1_brand
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

class AboutPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<AboutViewModel>()
        val pages = koinInject<WalletPlatformPages>()
        val urlOpener = LocalUrlOpener.current
        AboutScreen(
            uiState = viewModel.uiState,
            actions = AboutActions(
                onVersionClick = { navigation.slideFromRight(ReleaseNotesPage(ReleaseNotesPage.Input(false))) },
                onAppStatusClick = { navigation.slideFromRight(AppStatusPage()) },
                onTermsClick = { navigation.slideFromBottom(TermsPage()) },
                onPrivacyClick = { navigation.slideFromRight(pages.privacy()) },
                onLinkClick = urlOpener::open,
            ),
            onBackPress = navigation::navigateUpSafely,
        )
    }
}

internal class AboutActions(
    val onVersionClick: () -> Unit,
    val onAppStatusClick: () -> Unit,
    val onTermsClick: () -> Unit,
    val onPrivacyClick: () -> Unit,
    val onLinkClick: (String) -> Unit,
)

@Composable
internal fun AboutScreen(
    uiState: AboutUiState,
    actions: AboutActions,
    onBackPress: () -> Unit,
) {
    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Column {
            AppBar(
                title = stringResource(Res.string.SettingsAboutApp_Title),
                navigationIcon = { HsBackButton(onClick = onBackPress) }
            )

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(12.dp))
                VersionSection(uiState.appVersion, actions.onVersionClick)
                Spacer(Modifier.height(32.dp))
                InfoSection(uiState.termsShowAlert, actions)
                Spacer(Modifier.height(32.dp))
                WebSection(actions.onLinkClick)
                Spacer(Modifier.height(32.dp))
                JoinUsSection(actions.onLinkClick)
                VSpacer(32.dp)
            }
        }
    }
}

@Composable
private fun VersionSection(appVersion: String, onClick: () -> Unit) {
    CellUniversalLawrenceSection(
        listOf {
            SettingsCell(
                title = Res.string.SettingsAboutApp_AppVersion,
                icon = Res.drawable.ic_info_20,
                value = appVersion,
                onClick = onClick,
            )
        }
    )
}

@Composable
private fun InfoSection(termsShowAlert: Boolean, actions: AboutActions) {
    val alertPainter = if (termsShowAlert) settingsAlertPainter() else null
    CellUniversalLawrenceSection(
        listOf(
            {
                SettingsCell(
                    title = Res.string.Settings_AppStatus,
                    icon = Res.drawable.ic_app_status,
                    onClick = actions.onAppStatusClick,
                )
            },
            {
                SettingsCell(
                    title = Res.string.Settings_Terms,
                    icon = Res.drawable.ic_terms_20,
                    alertPainter = alertPainter,
                    onClick = actions.onTermsClick,
                )
            },
            {
                SettingsCell(
                    title = Res.string.Settings_Privacy,
                    icon = Res.drawable.ic_user_20,
                    onClick = actions.onPrivacyClick,
                )
            },
        )
    )
}

@Composable
private fun WebSection(onLinkClick: (String) -> Unit) {
    CellUniversalLawrenceSection(
        listOf({
            SettingsCell(
                title = Res.string.SettingsAboutApp_Github,
                icon = Res.drawable.ic_github_20,
                onClick = { onLinkClick(SharedAppConfig.appGithubLink) },
            )
        }, {
            SettingsCell(
                title = Res.string.SettingsAboutApp_Site,
                icon = Res.drawable.ic_globe,
                onClick = { onLinkClick(SharedAppConfig.appWebPageLink) },
            )
        })
    )
}

@Composable
private fun JoinUsSection(onLinkClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        subhead1_brand(text = stringResource(Res.string.Settings_JoinUs).uppercase())
    }
    CellUniversalLawrenceSection(
        listOf({
            SettingsCell(
                title = Res.string.Settings_Telegram,
                icon = Res.drawable.ic_telegram_filled_24,
                onClick = { onLinkClick(SharedAppConfig.appTelegramLink) },
            )
        }, {
            SettingsCell(
                title = Res.string.Settings_Twitter,
                icon = Res.drawable.ic_twitter_filled_24,
                onClick = { onLinkClick(SharedAppConfig.appTwitterLink) },
            )
        })
    )
    InfoText(text = stringResource(Res.string.Settings_JoinUs_Description))
}
