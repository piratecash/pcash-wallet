package cash.p.terminal.shared.desktop.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.rememberPremiumAction
import cash.p.terminal.feature.wallet.rememberSecretAction
import cash.p.terminal.modules.pin.SetDuressPinIntroPage
import cash.p.terminal.modules.settings.advancedsecurity.HiddenWalletTermsPage
import cash.p.terminal.modules.settings.security.autolock.AutoLockInterval
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.PageResumeEffect
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Appearance_BalanceAutoHide
import cash.p.terminal.resources.Appearance_BalanceAutoHide_Description
import cash.p.terminal.resources.AdvancedSecurity_CreateHiddenWallet
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SettingsSecurity_DuressPinDescription
import cash.p.terminal.resources.Settings_AutoLock
import cash.p.terminal.resources.Settings_SecurityCenter
import cash.p.terminal.resources.desktop_change_password_title
import cash.p.terminal.resources.desktop_duress_password_disable
import cash.p.terminal.resources.desktop_duress_password_edit
import cash.p.terminal.resources.desktop_duress_password_set
import cash.p.terminal.resources.desktop_security_lock_now
import cash.p.terminal.resources.desktop_transfer_password
import cash.p.terminal.resources.desktop_transfer_password_description
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.ic_delete_20
import cash.p.terminal.resources.ic_switch_wallet_24
import cash.p.terminal.shared.desktop.lock.ChangePasswordPage
import cash.p.terminal.shared.desktop.lock.EditDuressPasswordPage
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsSettingCell
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.SwitchWithText
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_brand
import cash.p.terminal.ui_compose.components.body_lucian
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

class DesktopSecurityPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel: DesktopSecurityViewModel = koinViewModel()
        val duressViewModel: DesktopDuressSectionViewModel = koinViewModel()
        val manageDuress = navigation.rememberSecretAction<Boolean> { duressSet ->
            navigation.slideFromRight(if (duressSet) EditDuressPasswordPage() else SetDuressPinIntroPage())
        }
        val disableDuress = navigation.rememberSecretAction(duressViewModel::onDisableClick)
        val openHiddenWalletTerms = navigation.rememberPremiumAction {
            navigation.slideFromRight(HiddenWalletTermsPage())
        }
        // The interval and the duress state are changed on other pages, so they are re-read when this one comes back.
        PageResumeEffect(
            onResume = {
                viewModel.onResume()
                duressViewModel.onResume()
            },
            onPause = {},
        )
        DesktopSecurityContent(
            uiState = viewModel.uiState,
            duressState = duressViewModel.uiState,
            actions = DesktopSecurityActions(
                onBalanceAutoHideChange = viewModel::onBalanceAutoHideChange,
                onTransferPasswordChange = viewModel::onTransferPasswordChange,
                onAutoLockClick = { navigation.slideFromRight(DesktopAutoLockIntervalsPage()) },
                onChangePasswordClick = { navigation.slideFromRight(ChangePasswordPage()) },
                onLockClick = viewModel::onLockClick,
                onDuressManageClick = { manageDuress(duressViewModel.uiState.set) },
                onDuressDisableClick = disableDuress,
                onHiddenWalletClick = openHiddenWalletTerms,
            ),
            onBack = navigation::navigateUpSafely,
        )
    }
}

internal class DesktopSecurityActions(
    val onBalanceAutoHideChange: (Boolean) -> Unit,
    val onTransferPasswordChange: (Boolean) -> Unit,
    val onAutoLockClick: () -> Unit,
    val onChangePasswordClick: () -> Unit,
    val onLockClick: () -> Unit,
    val onDuressManageClick: () -> Unit,
    val onDuressDisableClick: () -> Unit,
    val onHiddenWalletClick: () -> Unit,
)

@Composable
internal fun DesktopSecurityContent(
    uiState: DesktopSecurityUiState,
    duressState: DuressSectionUiState,
    actions: DesktopSecurityActions,
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
        Column(modifier = Modifier.padding(paddingValues).verticalScroll(rememberScrollState())) {
            VSpacer(12.dp)
            AccessSection(uiState, actions)
            VSpacer(32.dp)
            SwitchSection(
                title = stringResource(Res.string.Appearance_BalanceAutoHide),
                description = stringResource(Res.string.Appearance_BalanceAutoHide_Description),
                checked = uiState.balanceAutoHideEnabled,
                onCheckedChange = actions.onBalanceAutoHideChange,
            )
            VSpacer(20.dp)
            SwitchSection(
                title = stringResource(Res.string.desktop_transfer_password),
                description = stringResource(Res.string.desktop_transfer_password_description),
                checked = uiState.transferPasswordEnabled,
                onCheckedChange = actions.onTransferPasswordChange,
            )
            if (duressState.available) {
                VSpacer(20.dp)
                DuressSection(duressState.set, actions.onDuressManageClick, actions.onDuressDisableClick)
            }
            VSpacer(20.dp)
            CellUniversalLawrenceSection {
                HsSettingCell(
                    title = stringResource(Res.string.AdvancedSecurity_CreateHiddenWallet),
                    arrowPainter = painterResource(Res.drawable.ic_arrow_right),
                    onClick = actions.onHiddenWalletClick,
                )
            }
            VSpacer(24.dp)
        }
    }
}

@Composable
private fun AccessSection(uiState: DesktopSecurityUiState, actions: DesktopSecurityActions) {
    CellUniversalLawrenceSection(
        listOf(
            {
                HsSettingCell(
                    title = stringResource(Res.string.Settings_AutoLock),
                    arrowPainter = painterResource(Res.drawable.ic_arrow_right),
                    value = stringResource(uiState.autoLockInterval.title),
                    onClick = actions.onAutoLockClick,
                )
            },
            {
                HsSettingCell(
                    title = stringResource(Res.string.desktop_change_password_title),
                    arrowPainter = painterResource(Res.drawable.ic_arrow_right),
                    onClick = actions.onChangePasswordClick,
                )
            },
            {
                HsSettingCell(
                    title = stringResource(Res.string.desktop_security_lock_now),
                    arrowPainter = null,
                    onClick = actions.onLockClick,
                )
            },
        )
    )
}

@Composable
private fun DuressSection(set: Boolean, onManageClick: () -> Unit, onDisableClick: () -> Unit) {
    CellUniversalLawrenceSection(
        buildList<@Composable () -> Unit> {
            add {
                DuressCell(Res.drawable.ic_switch_wallet_24, ComposeAppTheme.colors.brandDefault, onManageClick) {
                    body_brand(
                        text = stringResource(
                            if (set) Res.string.desktop_duress_password_edit else Res.string.desktop_duress_password_set
                        )
                    )
                }
            }
            if (set) {
                add {
                    DuressCell(Res.drawable.ic_delete_20, ComposeAppTheme.colors.statusError, onDisableClick) {
                        body_lucian(text = stringResource(Res.string.desktop_duress_password_disable))
                    }
                }
            }
        }
    )
    InfoText(text = stringResource(Res.string.SettingsSecurity_DuressPinDescription))
}

@Composable
private fun DuressCell(
    icon: DrawableResource,
    tint: Color,
    onClick: () -> Unit,
    title: @Composable () -> Unit,
) {
    RowUniversal(modifier = Modifier.padding(horizontal = 16.dp), onClick = onClick) {
        Icon(
            painter = painterResource(icon),
            tint = tint,
            modifier = Modifier.size(24.dp),
            contentDescription = null,
        )
        HSpacer(16.dp)
        title()
    }
}

@Composable
private fun SwitchSection(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SectionUniversalLawrence {
        SwitchWithText(
            text = title,
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
    InfoText(text = description)
}

@Preview
@Composable
private fun DesktopSecurityContentPreview() {
    ComposeAppTheme {
        DesktopSecurityContent(
            uiState = DesktopSecurityUiState(
                autoLockInterval = AutoLockInterval.AFTER_1_MIN,
                balanceAutoHideEnabled = true,
                transferPasswordEnabled = false,
            ),
            duressState = DuressSectionUiState(available = true, set = true),
            actions = DesktopSecurityActions({}, {}, {}, {}, {}, {}, {}, {}),
            onBack = {},
        )
    }
}
