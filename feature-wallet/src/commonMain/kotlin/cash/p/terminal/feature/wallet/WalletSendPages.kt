package cash.p.terminal.feature.wallet

import androidx.compose.runtime.Composable
import cash.p.terminal.core.managers.EvmKitWrapper
import cash.p.terminal.modules.balance.OpenSendTokenSelect
import cash.p.terminal.modules.sendtokenselect.PrefilledData
import cash.p.terminal.modules.transactionInfo.options.SpeedUpCancelType
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
    ): HSPage?
    fun resendBitcoin(type: SpeedUpCancelType): HSPage?
    fun sendTokenSelect(openSend: OpenSendTokenSelect?): HSPage?
    fun send(
        wallet: Wallet,
        title: String,
        sendEntryPoint: KClass<out HSPage>,
        prefilledData: PrefilledData,
    ): HSPage?
    fun swapFrom(tokenIn: Token?): HSPage?
    fun multiSwapExchanges(pendingMultiSwapId: String?): HSPage?
    fun offlineBroadcast(initialInput: String): HSPage?

    /** The Zcash pool migration flow; [onClose] runs once it is over. */
    @Composable
    fun ZcashMigration(wallet: Wallet, onClose: () -> Unit)
}
