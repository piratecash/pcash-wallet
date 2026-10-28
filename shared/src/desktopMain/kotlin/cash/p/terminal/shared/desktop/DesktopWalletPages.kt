package cash.p.terminal.shared.desktop

import androidx.compose.runtime.Composable
import cash.p.terminal.core.TransactionSettingsStorage
import cash.p.terminal.feature.wallet.AppRestarter
import cash.p.terminal.feature.wallet.FaqTopic
import cash.p.terminal.feature.wallet.SecretActionAuthorizer
import cash.p.terminal.feature.wallet.WalletPlatformPages
import cash.p.terminal.feature.wallet.WalletSendPages
import cash.p.terminal.modules.backuplocal.BackupLocalPage
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusPage
import cash.p.terminal.modules.coin.CoinPage
import cash.p.terminal.modules.createaccount.CreateAccountPage
import cash.p.terminal.modules.manageaccount.backupkey.BackupKeyPage
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.multiswap.SwapPage
import cash.p.terminal.modules.multiswap.exchanges.MultiSwapExchangesPage
import cash.p.terminal.modules.restoreaccount.RestoreAccountPage
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.navigation.BackupKeyInput
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.QrScannerInput
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.shared.desktop.lock.ConfirmPasswordPage
import cash.p.terminal.shared.main.DesktopMainPage
import cash.p.terminal.ui_compose.CoinFragmentInput
import cash.p.terminal.ui_compose.components.UrlOpener
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.navigation.WalletPages
import io.horizontalsystems.core.entities.Blockchain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The desktop pages of the wallet screens: no camera, no hardware wallets, no app icons (entries hidden). */
class DesktopWalletPages(private val urlOpener: UrlOpener) :
    AppPages, WalletPages, WalletPlatformPages, WalletSendPages {
    override fun coin(coinUid: String): HSPage = CoinPage(CoinFragmentInput(coinUid))
    override fun createAccount(): HSPage = CreateAccountPage(null)
    override fun restoreAccount(): HSPage = RestoreAccountPage(null)
    override fun backupKey(input: BackupKeyInput): HSPage = BackupKeyPage(input)
    override fun backupLocal(account: Account): HSPage = BackupLocalPage(account)

    override fun qrScanner(input: QrScannerInput): HSPage =
        throw UnsupportedOperationException("No QR scanner on desktop: isQrScannerAvailable hides its entries")

    override fun swap(tokenOut: Token): HSPage = SwapPage(tokenOut = tokenOut)

    override val isQrScannerAvailable = false
    override fun hardwareWallet(input: ManageAccountsModule.Input?): HSPage? = null
    override fun resetToFactory(account: Account): HSPage? = null
    override fun faq(topic: FaqTopic): HSPage = DesktopMarkdownAssetPage(topic.assetPrefix)
    override fun appIcon(): HSPage? = null
    override val isBlockchainStatusAvailable = true
    override fun blockchainStatus(blockchain: Blockchain): HSPage = BlockchainStatusPage(blockchain)

    // Desktop has no in-app markdown reader for remote files, so every link opens in the browser.
    override fun openMarkdownOrWeblink(navigation: HSNavigation, url: String) = urlOpener.open(url)

    // Tangem cards need NFC: hardwareWallet() and resetToFactory() hide every way to these.
    override suspend fun accessCodeRecovery(): HSPage? = null
    override suspend fun changeAccessCode() = Unit
    override suspend fun forgotAccessCode(cardId: String) = Unit

    override val mainPage = DesktopMainPage::class
    override fun swapFrom(tokenIn: Token?): HSPage = SwapPage(tokenIn = tokenIn)
    override fun multiSwapExchanges(pendingMultiSwapId: String?, payCoreDate: Long?): HSPage =
        MultiSwapExchangesPage(pendingMultiSwapId, payCoreDate)
}

/** Desktop always has a password, so it is asked again before every secret (Android asks for a set PIN). */
class DesktopSecretActionAuthorizer(
    private val settings: TransactionSettingsStorage,
) : SecretActionAuthorizer {
    @Composable
    override fun <A> rememberAuthorization(navigation: HSNavigation, action: (A) -> Unit): (A) -> Unit {
        val launcher = navigation.rememberResultLauncher<ConfirmPasswordPage.Result, A> { result, arg ->
            if (result.success) action(arg)
        }
        return { arg -> launcher.slideFromBottom(ConfirmPasswordPage(), arg) }
    }

    @Composable
    override fun rememberHiddenTransactionsAuthorization(navigation: HSNavigation, action: () -> Unit): () -> Unit {
        val authorized = rememberAuthorization<Unit>(navigation) { action() }
        return { authorized(Unit) }
    }

    @Composable
    override fun <A> rememberTransferAuthorization(navigation: HSNavigation, action: (A) -> Unit): (A) -> Unit {
        val authorized = rememberAuthorization(navigation, action)
        return { arg -> if (settings.transferPasscodeEnabled) authorized(arg) else action(arg) }
    }
}

/** As Android starting a new task: the root watches [generation] and starts over with a fresh back stack. */
class DesktopAppRestarter : AppRestarter {
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    override fun restart() = _generation.update { it + 1 }
}
