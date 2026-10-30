package cash.p.terminal.modules.send.memo

import cash.p.terminal.core.ISendMemoAdapter
import cash.p.terminal.core.OfflineSignRequest
import cash.p.terminal.core.OfflineStellarSignRequest
import cash.p.terminal.core.OfflineThorchainSignRequest
import cash.p.terminal.core.SignedOfflineMemoTransaction
import cash.p.terminal.core.SignedOfflineStellarTransaction
import cash.p.terminal.entities.OfflineStellarRetryMetadata
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.stellarkit.StellarKit
import java.math.BigDecimal

sealed interface SendMemoChain {
    val memoMaxLength: Int
    val memoMaxBytes: Int?
    val registersPendingDraft: Boolean

    /** Syntax only: runs on the main thread, so it must never reach the network. */
    fun validateAddress(address: String, adapter: ISendMemoAdapter)

    fun offlineSignRequest(amount: BigDecimal, address: String, memo: String?): OfflineSignRequest

    fun offlineRetryMetadata(signed: SignedOfflineMemoTransaction): OfflineStellarRetryMetadata?

    data object Stellar : SendMemoChain {
        override val memoMaxLength = 120
        override val memoMaxBytes: Int? = null
        override val registersPendingDraft = false

        // The asset adapter's validate() also looks up the recipient's trustline over the network.
        override fun validateAddress(address: String, adapter: ISendMemoAdapter) =
            StellarKit.validateAddress(address)

        override fun offlineSignRequest(amount: BigDecimal, address: String, memo: String?) =
            OfflineStellarSignRequest(amount, address, memo)

        override fun offlineRetryMetadata(signed: SignedOfflineMemoTransaction) =
            (signed as? SignedOfflineStellarTransaction)?.let {
                OfflineStellarRetryMetadata(
                    sourceAccountId = it.sourceAccountId,
                    sequenceNumber = it.sequenceNumber,
                    validUntil = it.validUntil,
                )
            }
    }

    // THORChain and Maya share the Cosmos memo limit and the kit's ambiguous-broadcast contract.
    data object Thorchain : SendMemoChain {
        override val memoMaxLength = THORCHAIN_MEMO_MAX_BYTES
        override val memoMaxBytes: Int? = THORCHAIN_MEMO_MAX_BYTES
        override val registersPendingDraft = true

        // ThorchainAdapter.validate is a pure bech32 + prefix check.
        override fun validateAddress(address: String, adapter: ISendMemoAdapter) = adapter.validate(address)

        override fun offlineSignRequest(amount: BigDecimal, address: String, memo: String?) =
            OfflineThorchainSignRequest(amount, address, memo)

        override fun offlineRetryMetadata(signed: SignedOfflineMemoTransaction): OfflineStellarRetryMetadata? = null
    }

    companion object {
        private const val THORCHAIN_MEMO_MAX_BYTES = 250

        fun of(blockchainType: BlockchainType): SendMemoChain = when (blockchainType) {
            BlockchainType.Stellar -> Stellar
            BlockchainType.Thorchain,
            BlockchainType.Mayachain -> Thorchain

            else -> throw IllegalArgumentException("No memo send flow for $blockchainType")
        }
    }
}
