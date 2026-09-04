package cash.p.terminal.modules.manageaccount.privatekeys

import android.os.Parcelable
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cash.p.terminal.R
import cash.p.terminal.core.adapters.zcash.ZcashPrivateKeyType
import cash.p.terminal.core.authorizedAction
import cash.p.terminal.core.composablePage
import cash.p.terminal.modules.manageaccount.evmprivatekey.EvmPrivateKeyFragment
import cash.p.terminal.modules.manageaccount.generalprivatekey.GeneralPrivateKeyFragment
import cash.p.terminal.modules.manageaccount.showextendedkey.ShowExtendedKeyFragment
import cash.p.terminal.modules.manageaccount.stellarsecretkey.StellarSecretKeyFragment
import cash.p.terminal.modules.manageaccount.ui.KeyActionItem
import cash.p.terminal.modules.manageaccount.zcashkeys.ZcashKeysScreen
import cash.p.terminal.modules.manageaccount.zcashkeys.ZcashKeysViewModel
import cash.p.terminal.modules.manageaccount.zcashkeys.titleFor
import cash.p.terminal.navigation.navigateSafely
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.popBackStackSafely
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.ui_compose.BaseComposeFragment
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val PRIVATE_KEYS_PAGE = "private_keys"
private const val ZCASH_KEYS_PAGE = "zcash_keys"

class PrivateKeysFragment : BaseComposeFragment() {

    @Composable
    override fun GetContent(navController: NavController) {
        withInput<Account>(navController) { account ->
            PrivateKeysNavHost(navController, account)
        }
    }

}

@Composable
private fun PrivateKeysNavHost(fragmentNavController: NavController, account: Account) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = PRIVATE_KEYS_PAGE) {
        composable(PRIVATE_KEYS_PAGE) {
            ManageAccountScreen(
                navController = fragmentNavController,
                account = account,
                onZcashKeysClick = { navController.navigateSafely(ZCASH_KEYS_PAGE) },
            )
        }
        composablePage(ZCASH_KEYS_PAGE) {
            val viewModel = koinViewModel<ZcashKeysViewModel> { parametersOf(account.id) }
            val uiState = viewModel.uiState
            val transparentTitle = titleFor(ZcashPrivateKeyType.Transparent)
            val shieldedTitle = titleFor(ZcashPrivateKeyType.Shielded)

            LaunchedEffect(uiState.closeScreen) {
                if (uiState.closeScreen) navController.navigateUp()
            }
            LifecycleResumeEffect(Unit) {
                onPauseOrDispose { viewModel.cancelReveal() }
            }

            ZcashKeysScreen(
                uiState = uiState,
                onReveal = { type ->
                    fragmentNavController.authorizedAction { viewModel.reveal(type) }
                },
                onShowKey = {
                    uiState.revealed?.let { revealed ->
                        val title = when (revealed.type) {
                            ZcashPrivateKeyType.Transparent -> transparentTitle
                            ZcashPrivateKeyType.Shielded -> shieldedTitle
                        }
                        fragmentNavController.slideFromRight(
                            R.id.generalPrivateKeyFragment,
                            GeneralPrivateKeyFragment.Input(revealed.key, title)
                        )
                    }
                    viewModel.onKeyShown()
                },
                onDismissError = viewModel::onErrorShown,
                onClose = navController::navigateUpSafely,
            )
        }
    }
}

@Composable
fun ManageAccountScreen(
    navController: NavController,
    account: Account,
    onZcashKeysClick: () -> Unit,
) {
    val viewModel = viewModel<PrivateKeysViewModel>(factory = PrivateKeysModule.Factory(account))

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.PrivateKeys_Title),
                navigationIcon = {
                    HsBackButton(onClick = { navController.popBackStackSafely() })
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))
            PrivateKeyActions(
                viewState = viewModel.viewState,
                navController = navController,
                onZcashKeysClick = onZcashKeysClick,
            )
        }
    }
}

@Composable
private fun PrivateKeyActions(
    viewState: PrivateKeysModule.ViewState,
    navController: NavController,
    onZcashKeysClick: () -> Unit,
) {
    viewState.evmPrivateKey?.let { key ->
        AuthorizedKeyActionItem(
            navController = navController,
            titleRes = R.string.PrivateKeys_EvmPrivateKey,
            descriptionRes = R.string.PrivateKeys_EvmPrivateKeyDescription,
            destinationId = R.id.evmPrivateKeyFragment,
            input = EvmPrivateKeyFragment.Input(key),
        )
    }
    viewState.stellarSecretKey?.let { key ->
        AuthorizedKeyActionItem(
            navController = navController,
            titleRes = R.string.PrivateKeys_StellarSecretKey,
            descriptionRes = R.string.PrivateKeys_StellarSecretKeyDescription,
            destinationId = R.id.stellarSecretKeyFragment,
            input = StellarSecretKeyFragment.Input(key),
        )
    }
    viewState.bip32RootKey?.let { key ->
        AuthorizedKeyActionItem(
            navController = navController,
            titleRes = R.string.PrivateKeys_Bip32RootKey,
            descriptionRes = R.string.PrivateKeys_Bip32RootKeyDescription,
            destinationId = R.id.showExtendedKeyFragment,
            input = ShowExtendedKeyFragment.Input(key.hdKey, key.displayKeyType),
        )
    }
    viewState.accountExtendedPrivateKey?.let { key ->
        AuthorizedKeyActionItem(
            navController = navController,
            titleRes = R.string.PrivateKeys_AccountExtendedPrivateKey,
            descriptionRes = R.string.PrivateKeys_AccountExtendedPrivateKeyDescription,
            destinationId = R.id.showExtendedKeyFragment,
            input = ShowExtendedKeyFragment.Input(key.hdKey, key.displayKeyType),
        )
    }
    if (viewState.hasZcashKeys) {
        KeyActionItem(
            title = stringResource(R.string.private_keys_zec),
            description = stringResource(R.string.private_keys_zec_description),
            onClick = onZcashKeysClick,
        )
    }
}

@Composable
private fun AuthorizedKeyActionItem(
    navController: NavController,
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    @IdRes destinationId: Int,
    input: Parcelable,
) {
    KeyActionItem(
        title = stringResource(titleRes),
        description = stringResource(descriptionRes),
    ) {
        navController.authorizedAction {
            navController.slideFromRight(destinationId, input)
        }
    }
}
