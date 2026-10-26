package cash.p.terminal.modules.balance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.rememberTermsAcceptedAction
import cash.p.terminal.modules.createaccount.CreateAccountPage
import cash.p.terminal.feature.wallet.WalletPlatformPages
import cash.p.terminal.modules.importwallet.ImportWalletPage
import cash.p.terminal.modules.watchaddress.WatchAddressPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.koin.compose.koinInject
import cash.p.terminal.resources.ManageAccounts_CreateNewWallet
import cash.p.terminal.resources.ManageAccounts_ImportWallet
import cash.p.terminal.resources.ManageAccounts_WatchAddress
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.hardware_wallet
import cash.p.terminal.resources.icon_add_to_wallet_24

@Composable
fun BalanceNoAccount(
    navigation: HSNavigation,
    paddingValuesParent: PaddingValues
) {
    val walletPlatformPages = koinInject<WalletPlatformPages>()
    val hardwareWalletAvailable = remember { walletPlatformPages.hardwareWallet(null) != null }
    val openAfterTerms = navigation.rememberTermsAcceptedAction<HSPage>(navigation::slideFromRight)
    Column(
        modifier = Modifier
            .padding(bottom = paddingValuesParent.calculateBottomPadding())
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(
                    color = ComposeAppTheme.colors.surfacePlaceholder,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(48.dp),
                painter = painterResource(Res.drawable.icon_add_to_wallet_24),
                contentDescription = "",
                tint = ComposeAppTheme.colors.iconSecondary
            )
        }
        Spacer(Modifier.height(32.dp))
        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .testTag("onboarding_create_wallet"),
            title = stringResource(Res.string.ManageAccounts_CreateNewWallet),
            onClick = { openAfterTerms(CreateAccountPage(null)) }
        )
        Spacer(modifier = Modifier.height(16.dp))
        ButtonPrimaryDefault(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            title = stringResource(Res.string.ManageAccounts_ImportWallet),
            onClick = { openAfterTerms(ImportWalletPage(null)) }
        )
        if (hardwareWalletAvailable) {
            Spacer(modifier = Modifier.height(16.dp))
            ButtonPrimaryDefault(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp),
                title = stringResource(Res.string.hardware_wallet),
                onClick = { walletPlatformPages.hardwareWallet(null)?.let(openAfterTerms) }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        ButtonPrimaryTransparent(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            title = stringResource(Res.string.ManageAccounts_WatchAddress),
            onClick = {
                navigation.slideFromRight(WatchAddressPage(null))
            }
        )

    }
}
