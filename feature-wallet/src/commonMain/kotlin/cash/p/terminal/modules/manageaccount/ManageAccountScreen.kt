package cash.p.terminal.modules.manageaccount

import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.feature.wallet.rememberSecretAction
import cash.p.terminal.feature.wallet.FaqTopic
import cash.p.terminal.feature.wallet.openFaq
import cash.p.terminal.core.rememberPremiumAction
import cash.p.terminal.modules.balance.HeaderNote
import cash.p.terminal.modules.balance.ui.NoteError
import cash.p.terminal.modules.balance.ui.NoteWarning
import cash.p.terminal.modules.manageaccount.ManageAccountModule.BackupItem
import cash.p.terminal.modules.manageaccount.ui.rememberCopyWithHud
import cash.p.terminal.modules.manageaccount.ManageAccountModule.KeyAction
import cash.p.terminal.wallet.navigation.WalletPages
import cash.p.terminal.feature.wallet.WalletPlatformPages
import org.koin.compose.koinInject
import cash.p.terminal.modules.manageaccount.backupkey.BackupKeyPage
import cash.p.terminal.modules.manageaccount.privatekeys.PrivateKeysPage
import cash.p.terminal.modules.manageaccount.publickeys.PublicKeysPage
import cash.p.terminal.modules.manageaccount.recoveryphrase.RecoveryPhrasePage
import cash.p.terminal.navigation.BackupKeyInput
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.manageaccounts.ManageAccountsPage
import cash.p.terminal.feature.wallet.ResetToFactoryResult
import cash.p.terminal.modules.restoreaccount.RestoreAccountPage
import cash.p.terminal.modules.restoreaccount.duplicatewallet.DuplicateWalletInfoSheet
import cash.p.terminal.ui_compose.components.HsSettingCell
import cash.p.terminal.modules.unlinkaccount.UnlinkAccountSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.resources.ManageAccount_Save
import cash.p.terminal.resources.Res
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonSecondaryDefault
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsImage
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.body_lucian
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account
import cash.p.terminal.ui_compose.components.Body
import cash.p.terminal.navigation.navigateUpSafely
import org.jetbrains.compose.resources.DrawableResource
import cash.p.terminal.resources.AccountRecovery_MigrationRecommended
import cash.p.terminal.resources.AccountRecovery_MigrationRequired
import cash.p.terminal.resources.ManageAccount_LocalBackup
import cash.p.terminal.resources.ManageAccount_Name
import cash.p.terminal.resources.ManageAccount_RecoveryPhraseBackup
import cash.p.terminal.resources.ManageAccount_Unlink
import cash.p.terminal.resources.PrivateKeys_Title
import cash.p.terminal.resources.PublicKeys_Title
import cash.p.terminal.resources.RecoveryPhrase_Title
import cash.p.terminal.resources.RecoveryPhrase_monero_Title
import cash.p.terminal.resources.card_settings_access_code_recovery_title
import cash.p.terminal.resources.change_access_code
import cash.p.terminal.resources.details_row_subtitle_signed_hashes_format
import cash.p.terminal.resources.duplicate_wallet
import cash.p.terminal.resources.forgot_access_code
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.ic_attention_20
import cash.p.terminal.resources.ic_checkmark_20
import cash.p.terminal.resources.ic_copy_24px
import cash.p.terminal.resources.ic_delete_20
import cash.p.terminal.resources.ic_edit_24
import cash.p.terminal.resources.ic_file_24
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.resources.ic_key_20
import cash.p.terminal.resources.icon_binocule_20
import cash.p.terminal.resources.icon_paper_contract_20
import cash.p.terminal.resources.icon_unlocked_48
import cash.p.terminal.resources.icon_warning_2_20
import cash.p.terminal.resources.reset_to_factory_settings
import cash.p.terminal.resources.signed
import cash.p.terminal.resources.spend_key
import cash.p.terminal.resources.view_key

@Composable
internal fun ManageAccountScreen(
    navigation: HSNavigation,
    viewState: ManageAccountModule.ViewState,
    account: Account,
    onCloseClicked: () -> Unit,
    onSaveClicked: () -> Unit,
    onNameChanged: (String) -> Unit,
    onActionClick: (KeyAction) -> Unit,
) {
    val topPage = navigation.lastOrNull()
    val currentOnCloseClicked by rememberUpdatedState(onCloseClicked)
    val openAuthorized = navigation.rememberSecretAction<HSPage>(navigation::slideFromRight)
    val openSheetAuthorized = navigation.rememberSecretAction<HSPage>(navigation::slideFromBottom)
    if (shouldCloseManageAccount(viewState.closeScreen, topPage)) {
        LaunchedEffect(viewState.closeScreen, topPage) {
            navigation.removeLastUntil(ManageAccountPage::class, inclusive = true)
            currentOnCloseClicked()
        }
    }

    Column(modifier = Modifier.background(color = ComposeAppTheme.colors.backgroundBase)) {
        AppBar(
            title = viewState.title,
            navigationIcon = {
                HsBackButton(onClick = { navigation.navigateUpSafely() })
            },
            menuItems = listOf(
                MenuItem(
                    title = TranslatableString.ResString(Res.string.ManageAccount_Save),
                    onClick = onSaveClicked,
                    enabled = viewState.canSave
                )
            )
        )

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            HeaderText(stringResource(Res.string.ManageAccount_Name))

            FormsInput(
                modifier = Modifier.padding(horizontal = 16.dp),
                initial = viewState.title,
                hint = "",
                onValueChange = onNameChanged
            )

            viewState.signedHashes?.let {
                VSpacer(32.dp)
                CellUniversalLawrenceSection(
                    listOf {
                        HsSettingCell(
                            title = stringResource(Res.string.signed),
                            arrowPainter = null,
                            leadingPainter = painterResource(Res.drawable.ic_info_20),
                            value = TranslatableString.ResString(
                                Res.string.details_row_subtitle_signed_hashes_format,
                                it
                            ).getString(),
                        )
                    }
                )
            }

            when (viewState.headerNote) {
                HeaderNote.NonStandardAccount -> {
                    NoteError(
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 32.dp
                        ),
                        text = stringResource(Res.string.AccountRecovery_MigrationRequired),
                        onClick = {
                            navigation.openFaq(FaqTopic.MigrationRequired)
                        }
                    )
                }

                HeaderNote.NonRecommendedAccount -> {
                    NoteWarning(
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 32.dp
                        ),
                        text = stringResource(Res.string.AccountRecovery_MigrationRecommended),
                        onClick = {
                            navigation.openFaq(FaqTopic.MigrationRecommended)
                        },
                        onClose = null
                    )
                }

                HeaderNote.None -> Unit
            }

            KeyActions(
                viewState = viewState,
                account = account,
                onActionClick = onActionClick,
                navigation = navigation
            )

            if (viewState.backupActions.isNotEmpty()) {
                BackupActions(
                    viewState.backupActions,
                    account,
                    openSheetAuthorized
                )
            }

            if (viewState.canBeDuplicated) {
                VSpacer(32.dp)
                CellUniversalLawrenceSection(
                    listOf {
                        AccountActionWithInfoItem(
                            title = stringResource(Res.string.duplicate_wallet),
                            icon = painterResource(Res.drawable.ic_copy_24px),
                            onInfoClick = {
                                navigation.slideFromBottom(DuplicateWalletInfoSheet())
                            },
                            onClick = {
                                openAuthorized(
                                    RestoreAccountPage(
                                        ManageAccountsModule.Input(
                                            popOffOnSuccess = ManageAccountsPage::class,
                                            popOffInclusive = true,
                                            defaultRoute = RestoreAccountPage.ROUTE_DUPLICATE,
                                            accountId = account.id
                                        )
                                    )
                                )
                            }
                        )
                    })
            }

            VSpacer(32.dp)
            CellUniversalLawrenceSection(
                listOf {
                    RedActionItem(
                        title = stringResource(Res.string.ManageAccount_Unlink),
                        icon = painterResource(Res.drawable.ic_delete_20)
                    ) {
                        navigation.slideFromBottom(UnlinkAccountSheet(account))
                    }
                })
            VSpacer(32.dp)
        }
    }
}

internal fun shouldCloseManageAccount(closeScreen: Boolean, topPage: HSPage?): Boolean =
    closeScreen && topPage is ManageAccountPage

@Composable
private fun HSNavigation.rememberOpenResetToFactory(onReset: () -> Unit): (HSPage?) -> Unit {
    val launcher = rememberResultLauncher<ResetToFactoryResult> { if (it.success) onReset() }
    return { page -> page?.let { launcher.slideFromBottom(it) } }
}

@Composable
private fun KeyActions(
    viewState: ManageAccountModule.ViewState,
    account: Account,
    onActionClick: (KeyAction) -> Unit,
    navigation: HSNavigation
) {
    val walletPlatformPages = koinInject<WalletPlatformPages>()
    val openAuthorized = navigation.rememberSecretAction<HSPage>(navigation::slideFromRight)
    val openPremium = navigation.rememberPremiumAction<HSPage>(navigation::slideFromRight)
    val openPremiumAuthorized = navigation.rememberSecretAction(openPremium)
    val openResetToFactory = navigation.rememberOpenResetToFactory { onActionClick(KeyAction.ResetToFactorySettings) }
    val actionItems = mutableListOf<@Composable () -> Unit>()

    viewState.keyActions.forEach { keyAction ->
        when (keyAction) {
            KeyAction.RecoveryPhrase -> {
                actionItems.add {
                    AccountActionItem(
                        title = stringResource(Res.string.RecoveryPhrase_Title),
                        icon = painterResource(Res.drawable.icon_paper_contract_20)
                    ) {
                        openAuthorized(
                            RecoveryPhrasePage(
                                RecoveryPhrasePage.Input(
                                    account = account,
                                    recoveryPhraseType = RecoveryPhrasePage.RecoveryPhraseType.Mnemonic
                                )
                            )
                        )
                    }
                }
            }

            KeyAction.RecoveryPhraseMonero -> {
                actionItems.add {
                    AccountActionItem(
                        title = stringResource(Res.string.RecoveryPhrase_monero_Title),
                        icon = painterResource(Res.drawable.icon_paper_contract_20),
                        iconTint = ComposeAppTheme.colors.statusWarning
                    ) {
                        openPremiumAuthorized(
                            RecoveryPhrasePage(
                                RecoveryPhrasePage.Input(
                                    account = account,
                                    recoveryPhraseType = RecoveryPhrasePage.RecoveryPhraseType.Monero
                                )
                            )
                        )
                    }
                }
            }

            KeyAction.PrivateKeys -> {
                actionItems.add {
                    AccountActionItem(
                        title = stringResource(Res.string.PrivateKeys_Title),
                        icon = painterResource(Res.drawable.ic_key_20)
                    ) {
                        navigation.slideFromRight(PrivateKeysPage(account))
                    }
                }
            }

            KeyAction.PublicKeys -> {
                actionItems.add {
                    AccountActionItem(
                        title = stringResource(Res.string.PublicKeys_Title),
                        icon = painterResource(Res.drawable.icon_binocule_20)
                    ) {
                        navigation.slideFromRight(PublicKeysPage(account))
                    }
                }
            }

            KeyAction.ResetToFactorySettings -> {
                actionItems.add {
                    AccountActionItem(
                        title = stringResource(Res.string.reset_to_factory_settings),
                        icon = painterResource(Res.drawable.ic_delete_20)
                    ) {
                        openResetToFactory(walletPlatformPages.resetToFactory(account))
                    }
                }
            }

            KeyAction.AccessCodeRecovery -> {
                actionItems.add {
                    AccountActionItem(
                        title = stringResource(Res.string.card_settings_access_code_recovery_title),
                        icon = painterResource(Res.drawable.icon_unlocked_48)
                    ) {
                        onActionClick(keyAction)
                    }
                }
            }

            KeyAction.ChangeAccessCode -> {
                actionItems.add {
                    AccountActionItem(
                        title = stringResource(Res.string.change_access_code),
                        icon = painterResource(Res.drawable.ic_key_20)
                    ) {
                        onActionClick(keyAction)
                    }
                }
            }

            KeyAction.ForgotAccessCode -> {
                actionItems.add {
                    AccountActionItem(
                        title = stringResource(Res.string.forgot_access_code),
                        icon = painterResource(Res.drawable.icon_warning_2_20)
                    ) {
                        onActionClick(keyAction)
                    }
                }
            }

            KeyAction.ViewKey -> {
                actionItems.add {
                    AccountActionItem(
                        title = stringResource(Res.string.view_key),
                        icon = painterResource(Res.drawable.icon_binocule_20)
                    ) {
                        onActionClick(keyAction)
                    }
                }
            }

            KeyAction.SpendKey -> {
                actionItems.add {
                    AccountActionItem(
                        title = stringResource(Res.string.spend_key),
                        icon = painterResource(Res.drawable.icon_paper_contract_20)
                    ) {
                        onActionClick(keyAction)
                    }
                }
            }
        }
    }

    if (actionItems.isNotEmpty()) {
        VSpacer(32.dp)
        CellUniversalLawrenceSection(actionItems)
    }
}

@Composable
private fun BackupActions(
    backupActions: List<BackupItem>,
    account: Account,
    openAuthorized: (HSPage) -> Unit
) {
    val walletPages = koinInject<WalletPages>()
    val actionItems = mutableListOf<@Composable () -> Unit>()
    val infoItems = mutableListOf<@Composable () -> Unit>()

    backupActions.forEach { action ->
        when (action) {
            is BackupItem.ManualBackup -> {
                actionItems.add {
                    YellowActionItem(
                        title = stringResource(Res.string.ManageAccount_RecoveryPhraseBackup),
                        icon = painterResource(Res.drawable.ic_edit_24),
                        attention = action.showAttention,
                        completed = action.completed
                    ) {
                        openAuthorized(BackupKeyPage(BackupKeyInput(account.id)))
                    }
                }
            }

            is BackupItem.LocalBackup -> {
                actionItems.add {
                    YellowActionItem(
                        title = stringResource(Res.string.ManageAccount_LocalBackup),
                        icon = painterResource(Res.drawable.ic_file_24),
                        attention = action.showAttention
                    ) {
                        openAuthorized(walletPages.backupLocal(account))
                    }
                }
            }

            is BackupItem.InfoText -> {
                infoItems.add {
                    InfoText(text = stringResource(action.textRes))
                }
            }
        }
    }
    if (actionItems.isNotEmpty()) {
        VSpacer(32.dp)
        CellUniversalLawrenceSection(actionItems)
    }
    infoItems.forEach {
        it.invoke()
    }

}

@Composable
private fun RedActionItem(
    title: String,
    icon: Painter,
    onClick: () -> Unit
) {

    RowUniversal(
        onClick = onClick
    ) {
        Icon(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .size(24.dp),
            painter = icon,
            contentDescription = null,
            tint = ComposeAppTheme.colors.statusError
        )

        body_lucian(
            text = title,
        )
    }
}

@Composable
private fun AccountActionItem(
    title: String,
    icon: Painter? = null,
    iconTint: Color? = null,
    coinIconUrl: String? = null,
    coinIconPlaceholder: DrawableResource? = null,
    attention: Boolean = false,
    badge: String? = null,
    onClick: (() -> Unit)? = null
) {
    RowUniversal(
        onClick = onClick
    ) {
        icon?.let {
            Icon(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .size(24.dp),
                painter = icon,
                contentDescription = null,
                tint = iconTint ?: ComposeAppTheme.colors.iconSecondary
            )
        }

        if (coinIconUrl != null) {
            HsImage(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .size(20.dp),
                url = coinIconUrl,
                placeholder = coinIconPlaceholder
            )
        }

        body_leah(
            modifier = Modifier.weight(1f),
            text = title,
        )

        if (attention) {
            Icon(
                modifier = Modifier.padding(horizontal = 16.dp),
                painter = painterResource(Res.drawable.ic_attention_20),
                contentDescription = null,
                tint = ComposeAppTheme.colors.statusError
            )
            Spacer(modifier = Modifier.width(6.dp))
        }

        badge?.let {
            val copyWithHud = rememberCopyWithHud()

            ButtonSecondaryDefault(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = it,
                onClick = { copyWithHud(it) }
            )
        }

        onClick?.let {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_right),
                contentDescription = null,
                tint = ComposeAppTheme.colors.iconSecondary
            )
            HSpacer(16.dp)
        }
    }
}

@Composable
private fun AccountActionWithInfoItem(
    title: String,
    onInfoClick: () -> Unit,
    icon: Painter? = null,
    iconTint: Color? = null,
    onClick: (() -> Unit)? = null
) {
    RowUniversal(
        onClick = onClick
    ) {
        icon?.let {
            Icon(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .size(24.dp),
                painter = icon,
                contentDescription = null,
                tint = iconTint ?: ComposeAppTheme.colors.iconSecondary
            )
        }

        body_leah(
            modifier = Modifier.weight(1f),
            text = title,
        )

        Icon(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .clickable(
                    onClick = onInfoClick,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false)
                ),
            painter = painterResource(Res.drawable.ic_info_20),
            contentDescription = null,
            tint = ComposeAppTheme.colors.iconSecondary,
        )

        onClick?.let {
            Icon(
                painter = painterResource(Res.drawable.ic_arrow_right),
                contentDescription = null,
                tint = ComposeAppTheme.colors.iconSecondary
            )
            HSpacer(16.dp)
        }
    }
}

@Composable
private fun YellowActionItem(
    title: String,
    icon: Painter? = null,
    attention: Boolean = false,
    completed: Boolean = false,
    onClick: (() -> Unit)? = null
) {

    RowUniversal(
        onClick = onClick
    ) {
        icon?.let {
            Icon(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .size(24.dp),
                painter = icon,
                contentDescription = null,
                tint = ComposeAppTheme.colors.statusWarning
            )
        }

        Body(
            text = title,
            color = ComposeAppTheme.colors.statusWarning,
            modifier = Modifier.weight(1f),
        )

        if (attention) {
            Icon(
                modifier = Modifier.padding(horizontal = 16.dp),
                painter = painterResource(Res.drawable.ic_attention_20),
                contentDescription = null,
                tint = ComposeAppTheme.colors.statusError
            )
            HSpacer(6.dp)
        } else if (completed) {
            Icon(
                modifier = Modifier.padding(horizontal = 16.dp),
                painter = painterResource(Res.drawable.ic_checkmark_20),
                contentDescription = null,
                tint = ComposeAppTheme.colors.statusSuccess
            )
            HSpacer(6.dp)
        }
    }
}
