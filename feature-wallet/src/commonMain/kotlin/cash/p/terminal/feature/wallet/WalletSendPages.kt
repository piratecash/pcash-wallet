package cash.p.terminal.feature.wallet

import androidx.compose.runtime.Composable
import cash.p.terminal.core.managers.EvmKitWrapper
import cash.p.terminal.modules.balance.OpenSendTokenSelect
import cash.p.terminal.modules.send.offline.OfflineBroadcastPage
import cash.p.terminal.modules.send.SendPage
import cash.p.terminal.modules.sendtokenselect.PrefilledData
import cash.p.terminal.modules.sendtokenselect.SendTokenSelectPage
import cash.p.terminal.modules.transactionInfo.options.SpeedUpCancelType
import cash.p.terminal.modules.transactionInfo.options.TransactionSpeedUpCancelPage
import cash.p.terminal.modules.transactionInfo.resendbitcoin.ResendBitcoinPage
import cash.p.terminal.modules.zcashmigration.ZcashMigrationFlow
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import kotlin.reflect.KClass

/** Sending pages: `null` where the platform cannot send yet, and the user is told so. */
interface WalletSendPages {
    fun speedUpCancel(
        blockchainType: BlockchainType,
        type: SpeedUpCancelType,
        transactionHash: String,
        evmKitWrapper: EvmKitWrapper,
    ): HSPage? = TransactionSpeedUpCancelPage(
        TransactionSpeedUpCancelPage.Input(blockchainType, type, transactionHash),
        evmKitWrapper
    )

    fun resendBitcoin(type: SpeedUpCancelType): HSPage? = ResendBitcoinPage(ResendBitcoinPage.Input(type))
    fun sendTokenSelect(openSend: OpenSendTokenSelect?): HSPage? = SendTokenSelectPage(
        openSend?.let { SendTokenSelectPage.Input(it.blockchainTypes, it.tokenTypes, it.prefilledData) }
    )

    fun send(
        wallet: Wallet,
        title: String,
        sendEntryPoint: KClass<out HSPage>,
        prefilledData: PrefilledData,
    ): HSPage? = SendPage(SendPage.Input(wallet, title, sendEntryPoint, prefilledData))

    fun swapFrom(tokenIn: Token?): HSPage?
    fun multiSwapExchanges(pendingMultiSwapId: String?): HSPage?
    fun offlineBroadcast(initialInput: String): HSPage? =
        OfflineBroadcastPage(OfflineBroadcastPage.Input(initialInput = initialInput))

    /** The Zcash pool migration flow; [onClose] runs once it is over. */
    @Composable
    fun ZcashMigration(wallet: Wallet, onClose: () -> Unit) = ZcashMigrationFlow(wallet, onClose)
}
