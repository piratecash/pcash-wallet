package cash.p.terminal.core.adapters.thorchain

import io.horizontalsystems.thorchainkit.ThorchainKit
import io.horizontalsystems.thorchainkit.models.Address
import io.horizontalsystems.thorchainkit.models.Asset
import io.horizontalsystems.thorchainkit.models.SignedTransaction
import io.horizontalsystems.thorchainkit.transaction.Signer
import io.horizontalsystems.thorchainkit.transaction.ThorchainExternalSigner
import java.math.BigInteger

/** The kit has one overload per kind of signer; this picks the one the account signs with. */
internal sealed interface ThorchainSigning {
    suspend fun send(kit: ThorchainKit, to: Address, amount: BigInteger, denom: String, memo: String?): String

    suspend fun signSend(
        kit: ThorchainKit,
        to: Address,
        amount: BigInteger,
        denom: String,
        memo: String?,
    ): SignedTransaction

    suspend fun deposit(kit: ThorchainKit, asset: Asset, amount: BigInteger, memo: String): String

    class Seed(private val signer: Signer) : ThorchainSigning {
        override suspend fun send(kit: ThorchainKit, to: Address, amount: BigInteger, denom: String, memo: String?) =
            kit.send(to, amount, denom, memo, signer)

        override suspend fun signSend(
            kit: ThorchainKit,
            to: Address,
            amount: BigInteger,
            denom: String,
            memo: String?,
        ) = kit.signSend(to, amount, signer, denom, memo)

        override suspend fun deposit(kit: ThorchainKit, asset: Asset, amount: BigInteger, memo: String) =
            kit.deposit(asset, amount, memo, signer)
    }

    class Device(private val signer: ThorchainExternalSigner) : ThorchainSigning {
        override suspend fun send(kit: ThorchainKit, to: Address, amount: BigInteger, denom: String, memo: String?) =
            kit.send(to, amount, denom, memo, signer)

        override suspend fun signSend(
            kit: ThorchainKit,
            to: Address,
            amount: BigInteger,
            denom: String,
            memo: String?,
        ) = kit.signSend(to, amount, signer, denom, memo)

        override suspend fun deposit(kit: ThorchainKit, asset: Asset, amount: BigInteger, memo: String) =
            kit.deposit(asset, amount, memo, signer)
    }
}
