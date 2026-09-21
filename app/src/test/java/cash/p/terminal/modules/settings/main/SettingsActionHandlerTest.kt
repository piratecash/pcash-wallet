package cash.p.terminal.modules.settings.main

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.R
import cash.p.terminal.modules.manageaccount.dialogs.BackupRequiredDialog
import cash.p.terminal.modules.walletconnect.WCManager
import cash.p.terminal.navigation.QrScannerInput
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsActionHandlerTest {

    @get:Rule
    val compose = createComposeRule()

    private val navController = mockk<NavController>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private var walletConnectSupport: WCManager.SupportState = WCManager.SupportState.Supported
    private var tonConnectSupported = true
    private val viewModel = mockk<MainSettingsViewModel>(relaxed = true) {
        every { uiState } returns settingsContentTestState
        every { appVersion } returns "1.2.3"
        every { companyWebPage } returns "https://example.com"
        every { walletConnectSupportState } answers { walletConnectSupport }
        every { currentAccountSupportsTonConnect } answers { tonConnectSupported }
    }

    @Test
    fun settingsScreen_supportChangesBeforeClick_usesLatestConnectionSupport() {
        compose.setContent {
            ComposeAppTheme {
                SettingsScreen(navController, PaddingValues(), viewModel)
            }
        }

        walletConnectSupport = WCManager.SupportState.NotSupportedDueToNoActiveAccount
        tonConnectSupported = false
        compose.onNodeWithText("WalletConnect").performScrollTo().performClick()
        val application = ApplicationProvider.getApplicationContext<Application>()
        compose.onNodeWithText(application.getString(R.string.Settings_TonConnect))
            .performScrollTo()
            .performClick()

        verify { navController.navigate(R.id.wcErrorNoAccountFragment, null, any()) }
        verify(exactly = 0) { navController.navigate(R.id.wcListFragment, isNull(), any()) }
        verify { navController.navigate(match<NavDirections> {
            it.actionId == R.id.actionGlobalToAccountTypeNotSupportedDialog
        }, any<NavOptions>()) }
    }

    @Test
    fun handleSettingsAction_walletConnectSupportStates_navigatesToMatchingDestinations() {
        val account = Account(
            id = "account-id",
            name = "Wallet",
            type = AccountType.EvmAddress("0x1"),
            origin = AccountOrigin.Created,
            level = 0,
        )

        handle(SettingsAction.WalletConnect)
        walletConnectSupport = WCManager.SupportState.NotSupportedDueToNoActiveAccount
        handle(SettingsAction.WalletConnect)
        walletConnectSupport = WCManager.SupportState.NotSupportedDueToNonBackedUpAccount(account)
        handle(SettingsAction.WalletConnect)
        walletConnectSupport = WCManager.SupportState.NotSupported
        handle(SettingsAction.WalletConnect)

        verify { navController.navigate(R.id.wcListFragment, null, any()) }
        verify { navController.navigate(R.id.wcErrorNoAccountFragment, null, any()) }
        verify { navController.navigate(
            R.id.backupRequiredDialog,
            match<Bundle> { it.getParcelable("input", BackupRequiredDialog.Input::class.java)?.account == account },
            any(),
        ) }
        verify { navController.navigate(match<NavDirections> {
            it.actionId == R.id.actionGlobalToAccountTypeNotSupportedDialog
        }, any<NavOptions>()) }
    }

    @Test
    fun handleSettingsAction_offlineBroadcast_opensQrScannerWithTitleAndPaste() {
        handle(SettingsAction.OfflineBroadcast)

        verify { navController.navigate(
            R.id.qrScannerFragment,
            match<Bundle> { bundle ->
                bundle.getParcelable("input", QrScannerInput::class.java) ==
                        QrScannerInput("Raw transaction", showPasteButton = true)
            },
            any(),
        ) }
    }

    @Test
    fun handleSettingsAction_simpleActions_opensDestinationsWithRightSlide() {
        val destinations = listOf(
            SettingsAction.Donate to R.id.donateTokenSelectFragment,
            SettingsAction.MiniApp to R.id.miniAppFragment,
            SettingsAction.BlockchainSettings to R.id.blockchainSettingsFragment,
            SettingsAction.BackupManager to R.id.backupManagerFragment,
            SettingsAction.SecurityCenter to R.id.securitySettingsFragment,
            SettingsAction.Appearance to R.id.appearanceFragment,
            SettingsAction.BaseCurrency to R.id.baseCurrencySettingsFragment,
            SettingsAction.Language to R.id.languageSettingsFragment,
            SettingsAction.AddressChecker to R.id.addressCheckerFragment,
            SettingsAction.SwapProviders to R.id.swapProvidersSettingsFragment,
            SettingsAction.PremiumSettings to R.id.premiumSettingsFragment,
            SettingsAction.AdvancedSecurity to R.id.advancedSecurityFragment,
            SettingsAction.SoftwareUpdate to R.id.softwareUpdateFragment,
            SettingsAction.AboutApp to R.id.aboutAppFragment,
        )

        destinations.forEach { (action, destination) ->
            clearMocks(navController, answers = false)
            handle(action)
            verify { navController.navigate(
                destination,
                null,
                match<NavOptions> { it.enterAnim == R.anim.slide_from_right },
            ) }
        }
    }

    @Test
    fun handleSettingsAction_contactPayCoreState_opensMatchingDestination() {
        handle(SettingsAction.Contact(true))
        handle(SettingsAction.Contact(false))

        verify { navController.navigate(R.id.contactUsFragment, null, any()) }
        verify { navController.navigate(R.id.contactOptionsDialog, null, any()) }
    }

    private fun handle(action: SettingsAction) {
        handleSettingsAction(
            action,
            navController,
            viewModel,
            context,
            rawTxScanTitle = "Raw transaction",
            walletConnectTitle = "WalletConnect",
            tonConnectTitle = "TON Connect",
        )
    }
}
