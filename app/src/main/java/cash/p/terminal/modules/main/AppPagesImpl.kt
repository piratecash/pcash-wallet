package cash.p.terminal.modules.main

import androidx.compose.runtime.Composable
import cash.p.terminal.core.managers.EvmKitWrapper
import cash.p.terminal.core.managers.FaqManager
import cash.p.terminal.feature.wallet.FaqTopic
import cash.p.terminal.feature.wallet.WalletPlatformPages
import cash.p.terminal.feature.wallet.WalletSendPages
import cash.p.terminal.modules.activatetoken.ActivateTokenPage
import cash.p.terminal.modules.backuplocal.BackupLocalPage
import cash.p.terminal.modules.balance.OpenSendTokenSelect
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusPage
import cash.p.terminal.modules.coin.CoinPage
import cash.p.terminal.modules.createaccount.CreateAccountPage
import cash.p.terminal.modules.hardwarewallet.HardwareWalletPage
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.market.MarketPages
import cash.p.terminal.modules.markdown.MarkdownPage
import cash.p.terminal.modules.markdown.openMarkdownOrWeblink
import cash.p.terminal.modules.manageaccount.backupkey.BackupKeyPage
import cash.p.terminal.modules.multiswap.SwapPage
import cash.p.terminal.modules.multiswap.exchanges.MultiSwapExchangesPage
import cash.p.terminal.modules.nft.collection.NftCollectionPage
import cash.p.terminal.modules.qrscanner.QRScannerPage
import cash.p.terminal.modules.resettofactorysettings.ResetToFactorySettingsPage
import cash.p.terminal.modules.restoreaccount.RestoreAccountPage
import cash.p.terminal.modules.send.SendPage
import cash.p.terminal.modules.send.offline.OfflineBroadcastPage
import cash.p.terminal.modules.sendtokenselect.PrefilledData
import cash.p.terminal.modules.sendtokenselect.SendTokenSelectPage
import cash.p.terminal.modules.settings.appearance.AppIconPage
import cash.p.terminal.modules.transactionInfo.options.SpeedUpCancelType
import cash.p.terminal.modules.transactionInfo.options.TransactionSpeedUpCancelPage
import cash.p.terminal.modules.transactionInfo.resendbitcoin.ResendBitcoinPage
import cash.p.terminal.modules.zcashmigration.ZcashMigrationFlow
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.navigation.BackupKeyInput
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.QrScannerInput
import cash.p.terminal.tangem.domain.sdk.TangemSdkManager
import cash.p.terminal.tangem.ui.accesscoderecovery.AccessCodeRecoverySheet
import cash.p.terminal.ui_compose.CoinFragmentInput
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.navigation.WalletPages
import com.tangem.common.doOnSuccess
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.ui.dialogs.ConfirmationDialogSheet
import io.horizontalsystems.core.entities.BlockchainType
import kotlin.reflect.KClass

class AppPagesImpl(
    private val tangemSdkManager: TangemSdkManager,
) : AppPages, WalletPages, MarketPages, WalletPlatformPages, WalletSendPages {
    override fun coin(coinUid: String): HSPage = CoinPage(CoinFragmentInput(coinUid))
    override fun createAccount(): HSPage = CreateAccountPage(null)
    override fun restoreAccount(): HSPage = RestoreAccountPage(null)
    override fun backupKey(input: BackupKeyInput): HSPage = BackupKeyPage(input)
    override fun qrScanner(input: QrScannerInput): HSPage = QRScannerPage(input)
    override fun swap(tokenOut: Token): HSPage = SwapPage(tokenOut = tokenOut)
    override fun backupLocal(account: Account): HSPage = BackupLocalPage(account)
    override fun markdown(url: String): HSPage = MarkdownPage(MarkdownPage.Input(url, true))
    override fun nftCollection(collectionUid: String, blockchainUid: String): HSPage =
        NftCollectionPage(NftCollectionPage.Input(collectionUid, blockchainUid))

    override val isQrScannerAvailable = true
    override fun hardwareWallet(input: ManageAccountsModule.Input?): HSPage = HardwareWalletPage(input)
    override fun resetToFactory(account: Account): HSPage =
        ResetToFactorySettingsPage(ResetToFactorySettingsPage.Input(account))
    override fun faq(topic: FaqTopic): HSPage = FaqManager.faqPage(topic)
    override fun appIcon(): HSPage = AppIconPage()
    override val isBlockchainStatusAvailable = true
    override fun blockchainStatus(blockchain: Blockchain): HSPage = BlockchainStatusPage(blockchain)
    override fun openMarkdownOrWeblink(navigation: HSNavigation, url: String) =
        navigation.openMarkdownOrWeblink(url)

    override val mainPage = MainPage::class
    override fun speedUpCancel(
        blockchainType: BlockchainType,
        type: SpeedUpCancelType,
        transactionHash: String,
        evmKitWrapper: EvmKitWrapper,
    ): HSPage = TransactionSpeedUpCancelPage(
        TransactionSpeedUpCancelPage.Input(blockchainType, type, transactionHash),
        evmKitWrapper
    )

    override fun resendBitcoin(type: SpeedUpCancelType): HSPage = ResendBitcoinPage(ResendBitcoinPage.Input(type))
    override fun sendTokenSelect(openSend: OpenSendTokenSelect?): HSPage = SendTokenSelectPage(
        openSend?.let { SendTokenSelectPage.Input(it.blockchainTypes, it.tokenTypes, it.prefilledData) }
    )

    override fun send(
        wallet: Wallet,
        title: String,
        sendEntryPoint: KClass<out HSPage>,
        prefilledData: PrefilledData,
    ): HSPage = SendPage(SendPage.Input(wallet, title, sendEntryPoint, prefilledData))

    override fun swapFrom(tokenIn: Token?): HSPage = SwapPage(tokenIn = tokenIn)
    override fun multiSwapExchanges(pendingMultiSwapId: String?): HSPage =
        MultiSwapExchangesPage(pendingMultiSwapId)

    override fun offlineBroadcast(initialInput: String): HSPage =
        OfflineBroadcastPage(OfflineBroadcastPage.Input(initialInput = initialInput))

    @Composable
    override fun ZcashMigration(wallet: Wallet, onClose: () -> Unit) = ZcashMigrationFlow(wallet, onClose)
    override fun activateToken(wallet: Wallet): HSPage = ActivateTokenPage(wallet)

    override fun confirmation(
        title: String,
        warningText: String,
        actionButtonTitle: String,
        transparentButtonTitle: String,
        onConfirm: () -> Unit,
    ): HSPage = ConfirmationDialogSheet(
        title = title,
        icon = null,
        warningTitle = null,
        warningText = warningText,
        actionButtonTitle = actionButtonTitle,
        transparentButtonTitle = transparentButtonTitle,
        listener = object : ConfirmationDialogSheet.Listener {
            override fun onActionButtonClick() = onConfirm()
        },
    )

    override suspend fun accessCodeRecovery(): HSPage? {
        var page: HSPage? = null
        tangemSdkManager.scanCard(cardId = null, allowRequestAccessCodeFromRepository = false)
            .doOnSuccess { card ->
                page = AccessCodeRecoverySheet(
                    AccessCodeRecoverySheet.Input(card.userSettings.isUserCodeRecoveryAllowed)
                )
            }
        return page
    }

    override suspend fun changeAccessCode() {
        tangemSdkManager.setAccessCode(null)
    }

    override suspend fun forgotAccessCode(cardId: String) {
        tangemSdkManager.restoreAccessCode(cardId)
    }
}
