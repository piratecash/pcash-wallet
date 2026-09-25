package cash.p.terminal.modules.settings.main

import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material3.Icon
import androidx.compose.material.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import cash.p.terminal.R
import cash.p.terminal.core.managers.RateAppManager
import cash.p.terminal.feature.miniapp.ui.miniapp.MiniAppPage
import cash.p.terminal.modules.backuplocal.fullbackup.BackupManagerPage
import cash.p.terminal.modules.basecurrency.BaseCurrencySettingsPage
import cash.p.terminal.modules.blockchainsettings.BlockchainSettingsPage
import cash.p.terminal.modules.contacts.ContactsPage
import cash.p.terminal.modules.contacts.Mode
import cash.p.terminal.modules.manageaccount.dialogs.BackupRequiredSheet
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.manageaccounts.ManageAccountsPage
import cash.p.terminal.modules.multiswap.providersettings.SwapProvidersSettingsPage
import cash.p.terminal.modules.premium.about.AboutPremiumPage
import cash.p.terminal.modules.premium.settings.PremiumSettingsPage
import cash.p.terminal.modules.send.offline.OfflineBroadcastPage
import cash.p.terminal.modules.settings.about.AboutPage
import cash.p.terminal.modules.settings.about.ContactOptionsSheet
import cash.p.terminal.modules.settings.about.ContactUsPage
import cash.p.terminal.modules.settings.addresschecker.AddressCheckerPage
import cash.p.terminal.modules.settings.advancedsecurity.AdvancedSecurityPage
import cash.p.terminal.modules.settings.appearance.AppearancePage
import cash.p.terminal.modules.settings.donate.DonateTokenSelectPage
import cash.p.terminal.modules.settings.language.LanguageSettingsPage
import cash.p.terminal.modules.settings.security.SecuritySettingsPage
import cash.p.terminal.modules.softwareupdate.SoftwareUpdatePage
import cash.p.terminal.modules.tonconnect.TonConnectMainPage
import cash.p.terminal.modules.walletconnect.AccountTypeNotSupportedSheet
import cash.p.terminal.modules.walletconnect.WCErrorNoAccountSheet
import cash.p.terminal.modules.walletconnect.WCManager
import cash.p.terminal.modules.walletconnect.list.WCListPage
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.PageResumeEffect
import cash.p.terminal.navigation.openQrScanner
import cash.p.terminal.strings.helpers.Translator
import cash.p.terminal.ui.compose.components.BadgeText
import cash.p.terminal.ui.helpers.LinkHelper
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellSingleLineLawrenceSection
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.PremiumHeader
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionPremiumUniversalLawrence
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

@Composable
fun SettingsScreen(
    navigation: HSNavigation,
    paddingValues: PaddingValues,
    viewModel: MainSettingsViewModel = koinViewModel(),
) {
    PageResumeEffect(onResume = viewModel::refresh, onPause = {})
    Surface(color = ComposeAppTheme.colors.tyler) {
        Column {
            AppBar(
                stringResource(R.string.Settings_Title),
            )

            Column(
                modifier = Modifier
                    .padding(bottom = paddingValues.calculateBottomPadding())
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                SettingSections(viewModel, navigation)
                SettingsFooter(viewModel.appVersion, viewModel.companyWebPage)
            }
        }
    }
}

@Composable
private fun SettingSections(
    viewModel: MainSettingsViewModel,
    navigation: HSNavigation
) {
    val uiState = viewModel.uiState
    val context = LocalContext.current
    val appPages: AppPages = koinInject()
    val rawTxScanTitle = stringResource(R.string.offline_broadcast_title)
    val walletConnectTitle = stringResource(R.string.WalletConnect_Title)
    val tonConnectTitle = stringResource(R.string.TonConnect_Title)

    CellUniversalLawrenceSection(
        listOf {
            HsSettingCell(
                title = R.string.Settings_Donate,
                icon = R.drawable.ic_heart_filled_24,
                iconTint = ComposeAppTheme.colors.jacob,
                onClick = {
                    navigation.slideFromRight(DonateTokenSelectPage())
                }
            )
        }
    )

    VSpacer(32.dp)

    CellUniversalLawrenceSection(
        listOf {
            HsSettingCell(
                R.string.settings_mini_app,
                R.drawable.ic_uwt2_24,
                onClick = {
                    navigation.slideFromRight(MiniAppPage())
                }
            )
        }
    )

    VSpacer(32.dp)

    CellUniversalLawrenceSection(
        listOf({
            HsSettingCell(
                R.string.SettingsSecurity_ManageKeys,
                R.drawable.ic_wallet_20,
                showAlert = uiState.manageWalletShowAlert,
                onClick = {
                    navigation.slideFromRight(ManageAccountsPage(ManageAccountsModule.Mode.Manage))
                }
            )
        }, {
            HsSettingCell(
                R.string.BlockchainSettings_Title,
                R.drawable.ic_blocks_20,
                onClick = {
                    navigation.slideFromRight(BlockchainSettingsPage())
                }
            )
        }, {
            HsSettingCell(
                R.string.Settings_WalletConnect,
                R.drawable.ic_wallet_connect_20,
                value = (uiState.wcCounterType as? MainSettingsModule.CounterType.SessionCounter)?.number?.toString(),
                counterBadge = (
                        uiState.wcCounterType as?
                                MainSettingsModule.CounterType.PendingRequestCounter
                        )?.number?.toString(),
                onClick = {
                    when (val state = viewModel.walletConnectSupportState) {
                        WCManager.SupportState.Supported -> {
                            navigation.slideFromRight(WCListPage(null))
                        }

                        WCManager.SupportState.NotSupportedDueToNoActiveAccount -> {
                            navigation.slideFromBottom(WCErrorNoAccountSheet())
                        }

                        is WCManager.SupportState.NotSupportedDueToNonBackedUpAccount -> {
                            val text = Translator.getString(R.string.WalletConnect_Error_NeedBackup)
                            navigation.slideFromBottom(
                                BackupRequiredSheet(BackupRequiredSheet.Input(state.account, text))
                            )
                        }

                        is WCManager.SupportState.NotSupported -> {
                            navigation.slideFromBottom(
                                AccountTypeNotSupportedSheet(
                                    AccountTypeNotSupportedSheet.Input(
                                        iconResId = R.drawable.ic_wallet_connect_24,
                                        titleResId = R.string.WalletConnect_Title,
                                        connectionLabel = walletConnectTitle
                                    )
                                )
                            )
                        }
                    }
                }
            )
        }, {
            HsSettingCell(
                title = R.string.Settings_TonConnect,
                icon = R.drawable.ic_ton_connect_24,
                value = null,
                counterBadge = null,
                onClick = {
                    if (viewModel.currentAccountSupportsTonConnect) {
                        navigation.slideFromRight(TonConnectMainPage(null))
                    } else {
                        navigation.slideFromBottom(
                            AccountTypeNotSupportedSheet(
                                AccountTypeNotSupportedSheet.Input(
                                    iconResId = R.drawable.ic_ton_connect_24,
                                    titleResId = R.string.TonConnect_Title,
                                    connectionLabel = tonConnectTitle
                                )
                            )
                        )
                    }
                }
            )
        }, {
            HsSettingCell(
                R.string.BackupManager_Title,
                R.drawable.ic_file_24,
                onClick = {
                    navigation.slideFromRight(BackupManagerPage())
                }
            )
        }
        )
    )

    VSpacer(32.dp)

    CellUniversalLawrenceSection(
        listOf(
            {
                HsSettingCell(
                    R.string.Settings_SecurityCenter,
                    R.drawable.ic_security,
                    newBadgeText = if (uiState.securityCenterShowNewBadge) {
                        stringResource(R.string.badge_new)
                    } else {
                        null
                    },
                    showAlert = uiState.securityCenterShowAlert,
                    onClick = {
                        navigation.slideFromRight(SecuritySettingsPage())
                    }
                )
            },
            {
                HsSettingCell(
                    R.string.Contacts,
                    R.drawable.ic_user_20,
                    onClick = {
                        navigation.slideFromRight(ContactsPage(ContactsPage.Input(Mode.Full)))
                    }
                )
            },
            {
                HsSettingCell(
                    R.string.Settings_Appearance,
                    R.drawable.ic_brush_20,
                    onClick = {
                        navigation.slideFromRight(AppearancePage())
                    }
                )
            },
            {
                HsSettingCell(
                    R.string.Settings_BaseCurrency,
                    R.drawable.ic_currency,
                    value = uiState.baseCurrencyCode,
                    onClick = {
                        navigation.slideFromRight(BaseCurrencySettingsPage())
                    }
                )
            },
            {
                HsSettingCell(
                    R.string.Settings_Language,
                    R.drawable.ic_language,
                    value = uiState.currentLanguage,
                    onClick = {
                        navigation.slideFromRight(LanguageSettingsPage())
                    }
                )
            }
        )
    )

    VSpacer(32.dp)
    CellUniversalLawrenceSection(
        listOf({
            HsSettingCell(
                title = R.string.address_checker_title,
                icon = R.drawable.ic_radar_24,
                onClick = {
                    navigation.slideFromRight(AddressCheckerPage())
                }
            )
        }, {
            HsSettingCell(
                title = R.string.swap_providers_title,
                icon = R.drawable.ic_swap_24,
                onClick = {
                    navigation.slideFromRight(SwapProvidersSettingsPage())
                }
            )
        })
    )
    VSpacer(32.dp)
    CellUniversalLawrenceSection(
        listOf({
            HsSettingCell(
                title = R.string.offline_broadcast_title,
                icon = R.drawable.ic_send_24,
                onClick = {
                    navigation.openQrScanner(
                        appPages = appPages,
                        title = rawTxScanTitle,
                        showPasteButton = true,
                    ) { scannedText ->
                        navigation.slideFromRight(
                            OfflineBroadcastPage(OfflineBroadcastPage.Input(initialInput = scannedText))
                        )
                    }
                }
            )
        })
    )

    VSpacer(24.dp)

    PremiumHeader()

    SectionPremiumUniversalLawrence {
        HsSettingCell(
            title = R.string.about_premium,
            icon = R.drawable.ic_info_20,
            iconTint = ComposeAppTheme.colors.jacob,
            onClick = {
                navigation.slideFromBottom(AboutPremiumPage(null))
            }
        )
        HsSettingCell(
            title = R.string.premium_settings,
            icon = R.drawable.ic_settings,
            iconTint = ComposeAppTheme.colors.jacob,
            showAlert = uiState.premiumSettingsShowAlert,
            onClick = {
                navigation.slideFromRight(PremiumSettingsPage())
            }
        )
        HsSettingCell(
            title = R.string.advanced_security,
            icon = R.drawable.ic_shield_24,
            iconTint = ComposeAppTheme.colors.jacob,
            onClick = {
                navigation.slideFromRight(AdvancedSecurityPage())
            }
        )
    }

    VSpacer(32.dp)
    CellUniversalLawrenceSection(
        listOf({
            HsSettingCell(
                R.string.software_update_title,
                R.drawable.ic_refresh,
                showAlert = uiState.isUpdateAvailable,
                onClick = {
                    navigation.slideFromRight(SoftwareUpdatePage())
                }
            )
        }, {
            HsSettingCell(
                R.string.SettingsAboutApp_Title,
                R.drawable.ic_about_app_20,
                showAlert = uiState.aboutAppShowAlert,
                onClick = {
                    navigation.slideFromRight(AboutPage())
                }
            )
        }, {
            HsSettingCell(
                R.string.Settings_RateUs,
                R.drawable.ic_star_20,
                onClick = {
                    RateAppManager.openPlayMarket(context)
                }
            )
        }, {
            HsSettingCell(
                R.string.Settings_ShareThisWallet,
                R.drawable.ic_share_20,
                onClick = {
                    shareAppLink(uiState.appWebPageLink, context)
                }
            )
        }, {
            HsSettingCell(
                R.string.SettingsContact_Title,
                R.drawable.ic_mail_24,
                onClick = {
                    if (uiState.isPayCoreEnabled) {
                        navigation.slideFromRight(ContactUsPage())
                    } else {
                        navigation.slideFromBottom(ContactOptionsSheet(null))
                    }
                },
            )
        })
    )

    VSpacer(32.dp)
}

@Composable
fun HsSettingCell(
    @StringRes title: Int,
    @DrawableRes icon: Int? = null,
    iconTint: Color? = null,
    value: String? = null,
    counterBadge: String? = null,
    newBadgeText: String? = null,
    showAlert: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClick
    ) {
        icon?.let {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(id = icon),
                contentDescription = null,
                tint = iconTint ?: ComposeAppTheme.colors.grey
            )
        }
        body_leah(
            text = stringResource(title),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = if (icon != null) 16.dp else 0.dp, end = 16.dp)
        )
        Spacer(Modifier.weight(1f))

        if (counterBadge != null) {
            BadgeText(
                modifier = Modifier.padding(horizontal = 8.dp),
                text = counterBadge
            )
        } else if (value != null) {
            subhead1_grey(
                text = value,
                maxLines = 1,
                modifier = Modifier.padding(
                    horizontal =
                        if (onClick != null) 8.dp else 0.dp
                )
            )
        }

        if (newBadgeText != null) {
            BadgeText(
                modifier = Modifier.padding(horizontal = 8.dp),
                text = newBadgeText,
                background = ComposeAppTheme.colors.issykBlue,
            )
        }

        if (showAlert) {
            Image(
                modifier = Modifier.size(20.dp),
                painter = painterResource(id = R.drawable.ic_attention_red_20),
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
        }
        if (onClick != null) {
            Image(
                modifier = Modifier.size(20.dp),
                painter = painterResource(id = R.drawable.ic_arrow_right),
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun SettingsFooter(appVersion: String, companyWebPage: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        caption_grey(
            text = stringResource(
                R.string.Settings_InfoTitleWithVersion,
                appVersion
            ).uppercase()
        )
        Divider(
            modifier = Modifier
                .width(100.dp)
                .padding(top = 8.dp, bottom = 4.5.dp),
            thickness = 0.5.dp,
            color = ComposeAppTheme.colors.steel20
        )
        Text(
            text = stringResource(R.string.Settings_InfoSubtitle),
            style = ComposeAppTheme.typography.micro,
            color = ComposeAppTheme.colors.grey,
        )
        Image(
            modifier = Modifier
                .padding(top = 32.dp)
                .size(32.dp)
                .clickable {
                    LinkHelper.openLinkInAppBrowser(context, companyWebPage)
                },
            painter = painterResource(id = R.drawable.ic_company_logo),
            contentDescription = null,
        )
        caption_grey(
            modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
            text = stringResource(R.string.Settings_CompanyName),
        )
    }
}

private fun shareAppLink(appLink: String, context: Context) {
    val shareMessage =
        cash.p.terminal.strings.helpers.Translator.getString(R.string.SettingsShare_Text) + "\n" + appLink + "\n"
    val shareIntent = Intent(Intent.ACTION_SEND)
    shareIntent.type = "text/plain"
    shareIntent.putExtra(Intent.EXTRA_TEXT, shareMessage)
    context.startActivity(
        Intent.createChooser(
            shareIntent,
            cash.p.terminal.strings.helpers.Translator.getString(R.string.SettingsShare_Title)
        )
    )
}

@Preview
@Composable
private fun previewSettingsScreen() {
    cash.p.terminal.ui_compose.theme.ComposeAppTheme {
        Column {
            CellSingleLineLawrenceSection(
                listOf({
                    HsSettingCell(
                        R.string.Settings_Faq,
                        R.drawable.ic_faq_20,
                        showAlert = true,
                        onClick = { }
                    )
                }, {
                    HsSettingCell(
                        R.string.Guides_Title,
                        R.drawable.ic_academy_20,
                        onClick = { }
                    )
                })
            )

            Spacer(Modifier.height(32.dp))

            CellSingleLineLawrenceSection(
                listOf {
                    HsSettingCell(
                        R.string.Settings_WalletConnect,
                        R.drawable.ic_wallet_connect_20,
                        counterBadge = "13",
                        onClick = { }
                    )
                }
            )
        }
    }
}
