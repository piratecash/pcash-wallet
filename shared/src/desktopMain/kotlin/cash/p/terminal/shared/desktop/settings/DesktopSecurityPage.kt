package cash.p.terminal.shared.desktop.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.settings.security.autolock.AutoLockInterval
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.PageResumeEffect
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Settings_AutoLock
import cash.p.terminal.resources.Settings_SecurityCenter
import cash.p.terminal.resources.desktop_change_password_title
import cash.p.terminal.resources.desktop_security_lock_now
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.shared.desktop.lock.ChangePasswordPage
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsSettingCell
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

class DesktopSecurityPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: DesktopSecurityViewModel = koinViewModel()
        // The interval is chosen on another page, so it is re-read when this one comes back.
        PageResumeEffect(onResume = viewModel::onResume, onPause = {})
        DesktopSecurityContent(
            autoLockInterval = viewModel.autoLockInterval,
            onAutoLockClick = { navigation.slideFromRight(DesktopAutoLockIntervalsPage()) },
            onChangePasswordClick = { navigation.slideFromRight(ChangePasswordPage()) },
            onLockClick = viewModel::onLockClick,
            onBack = navigation::navigateUpSafely,
        )
    }
}

@Composable
internal fun DesktopSecurityContent(
    autoLockInterval: AutoLockInterval,
    onAutoLockClick: () -> Unit,
    onChangePasswordClick: () -> Unit,
    onLockClick: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.Settings_SecurityCenter),
                navigationIcon = { HsBackButton(onClick = onBack) },
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            VSpacer(12.dp)
            CellUniversalLawrenceSection(
                listOf(
                    {
                        HsSettingCell(
                            title = stringResource(Res.string.Settings_AutoLock),
                            arrowPainter = painterResource(Res.drawable.ic_arrow_right),
                            value = stringResource(autoLockInterval.title),
                            onClick = onAutoLockClick,
                        )
                    },
                    {
                        HsSettingCell(
                            title = stringResource(Res.string.desktop_change_password_title),
                            arrowPainter = painterResource(Res.drawable.ic_arrow_right),
                            onClick = onChangePasswordClick,
                        )
                    },
                    {
                        HsSettingCell(
                            title = stringResource(Res.string.desktop_security_lock_now),
                            arrowPainter = null,
                            onClick = onLockClick,
                        )
                    },
                )
            )
        }
    }
}

@Preview
@Composable
private fun DesktopSecurityContentPreview() {
    ComposeAppTheme {
        DesktopSecurityContent(
            autoLockInterval = AutoLockInterval.AFTER_1_MIN,
            onAutoLockClick = {},
            onChangePasswordClick = {},
            onLockClick = {},
            onBack = {},
        )
    }
}
