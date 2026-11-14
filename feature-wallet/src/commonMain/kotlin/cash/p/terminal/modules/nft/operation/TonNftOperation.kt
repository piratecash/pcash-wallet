package cash.p.terminal.modules.nft.operation

import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.NftBroadcastResult
import cash.p.terminal.core.TonFeeEstimate

/** One owner's part of an NFT operation: [execute] must not throw for a send failure, it reports it in the result. */
interface TonNftOperation {
    suspend fun estimate(adapter: ISendTonAdapter, nftAddresses: List<String>): TonFeeEstimate
    suspend fun execute(adapter: ISendTonAdapter, nftAddresses: List<String>): NftBroadcastResult
}

object DomainRenewalOperation : TonNftOperation {
    override suspend fun estimate(adapter: ISendTonAdapter, nftAddresses: List<String>) =
        adapter.estimateDomainRenewalFee(nftAddresses)

    override suspend fun execute(adapter: ISendTonAdapter, nftAddresses: List<String>) =
        adapter.renewDomains(nftAddresses)
}
