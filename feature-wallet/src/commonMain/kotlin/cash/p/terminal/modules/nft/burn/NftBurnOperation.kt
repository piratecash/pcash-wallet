package cash.p.terminal.modules.nft.burn

import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.NftBroadcastResult
import cash.p.terminal.core.TonFeeEstimate
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.modules.nft.operation.TonNftOperation
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.tonkit.FriendlyAddress
import kotlinx.coroutines.CancellationException

/** The zero account (tonkeeper-zero.ton): nobody holds its key, so what it receives is gone. */
internal const val TON_BURN_ADDRESS = "UQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAJKZ"

/** Transfers a single NFT to [TON_BURN_ADDRESS]. */
object NftBurnOperation : TonNftOperation {
    private val burnAddress = FriendlyAddress.parse(TON_BURN_ADDRESS)

    override suspend fun estimate(adapter: ISendTonAdapter, nftAddresses: List<String>): TonFeeEstimate =
        adapter.estimateNftTransferFee(nftAddresses.single(), burnAddress, null)

    override suspend fun execute(
        wallet: Wallet,
        adapter: ISendTonAdapter,
        nftAddresses: List<String>,
    ): NftBroadcastResult {
        val nftAddress = nftAddresses.single()
        try {
            adapter.sendNftTransfer(nftAddress, burnAddress, null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return NftBroadcastResult(emptyList(), error = e)
        }
        getKoinInstance<LocallyCreatedTransactionRepository>()
            .markNftTransferCreated(wallet.account.id, nftAddress, TON_BURN_ADDRESS)
        return NftBroadcastResult(listOf(nftAddress), error = null)
    }
}
