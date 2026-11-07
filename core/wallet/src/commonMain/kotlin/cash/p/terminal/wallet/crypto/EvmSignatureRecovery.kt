package cash.p.terminal.wallet.crypto

import io.horizontalsystems.ethereumkit.crypto.CryptoUtils
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.GasPrice
import io.horizontalsystems.ethereumkit.models.RawTransaction
import io.horizontalsystems.ethereumkit.models.Signature
import io.horizontalsystems.ethereumkit.spv.core.toBytes
import io.horizontalsystems.ethereumkit.spv.rlp.RLP
import io.horizontalsystems.hdwalletkit.ECKey
import java.math.BigInteger

/**
 * EVM signature recovery shared by hardware-wallet signers (Trezor, Tangem, Ledger). Hardware devices
 * return only the raw `(r, s)` (and sometimes `v`) of a signature, so we have to rebuild the
 * signing hash ourselves and recover the public key / sender address from it — either to find the
 * recovery id (Tangem returns no `v`) or to verify the device signed with the expected account.
 */
object EvmSignatureRecovery {

    private const val EIP155_V_OFFSET = 35
    private const val PRE_EIP155_V_OFFSET = 27
    private const val UNCOMPRESSED_KEY_PREFIX_SIZE = 1
    private const val ADDRESS_BYTE_OFFSET = 12
    private const val SIGNATURE_COMPONENT_SIZE = 32
    private val EIP1559_TX_TYPE = byteArrayOf(0x02)
    private val SECP256K1_ORDER = BigInteger(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
        16,
    )

    /** Keccak-256 of the transaction's signing preimage. */
    fun signingHash(rawTransaction: RawTransaction, chainId: Int): ByteArray =
        CryptoUtils.sha3(unsignedTransaction(rawTransaction, chainId))

    /** The unsigned transaction a device signs: EIP-155 for legacy, typed for EIP-1559. */
    fun unsignedTransaction(rawTransaction: RawTransaction, chainId: Int): ByteArray =
        when (val gasPrice = rawTransaction.gasPrice) {
            is GasPrice.Legacy ->
                RLP.encodeList(
                    RLP.encodeLong(rawTransaction.nonce),
                    RLP.encodeLong(gasPrice.legacyGasPrice),
                    RLP.encodeLong(rawTransaction.gasLimit),
                    RLP.encodeElement(rawTransaction.to.raw),
                    RLP.encodeBigInteger(rawTransaction.value),
                    RLP.encodeElement(rawTransaction.data),
                    RLP.encodeInt(chainId),
                    RLP.encodeElement(ByteArray(0)),
                    RLP.encodeElement(ByteArray(0))
                )

            is GasPrice.Eip1559 ->
                EIP1559_TX_TYPE + RLP.encodeList(
                    RLP.encodeInt(chainId),
                    RLP.encodeLong(rawTransaction.nonce),
                    RLP.encodeLong(gasPrice.maxPriorityFeePerGas),
                    RLP.encodeLong(gasPrice.maxFeePerGas),
                    RLP.encodeLong(rawTransaction.gasLimit),
                    RLP.encodeElement(rawTransaction.to.raw),
                    RLP.encodeBigInteger(rawTransaction.value),
                    RLP.encodeElement(rawTransaction.data),
                    RLP.encode(arrayOf<Any>())
                )
        }

    /** The `v` a signed transaction carries for recovery id [recId]: EIP-155 for legacy, the parity for typed. */
    fun transactionV(recId: Int, gasPrice: GasPrice, chainId: Int): Int = when (gasPrice) {
        is GasPrice.Legacy -> recId + if (chainId == 0) PRE_EIP155_V_OFFSET else EIP155_V_OFFSET + 2 * chainId
        is GasPrice.Eip1559 -> recId
    }

    /** Recovers the sender address of a signed transaction, or null if it cannot be recovered. */
    fun recoverSenderAddress(
        rawTransaction: RawTransaction,
        signature: Signature,
        chainId: Int
    ): Address? {
        val recId = recoveryId(signature.v, chainId, rawTransaction.gasPrice)
        if (recId < 0) return null
        val publicKey = tryRecoverPublicKey(
            recId = recId,
            r = BigInteger(1, signature.r),
            s = BigInteger(1, signature.s),
            messageHash = signingHash(rawTransaction, chainId),
            compressed = false
        ) ?: return null
        return pubKeyToAddress(publicKey)
    }

    /**
     * Keccak-256 of the EIP-191 `personal_sign` preimage. Exact copy of the preimage built by
     * the fork's `EthSigner.signByteArray`, so hardware signers produce the same hash mnemonic
     * signing would, keeping the recovered address identical either way.
     */
    fun personalSignHash(message: ByteArray): ByteArray {
        val prefix = "Ethereum Signed Message:\n" + message.size
        return CryptoUtils.sha3(prefix.toByteArray() + message)
    }

    /** Recovers the signer address of a message signature, or null if it cannot be recovered. */
    fun recoverMessageAddress(messageHash: ByteArray, r: BigInteger, s: BigInteger, recId: Int): Address? {
        val publicKey = tryRecoverPublicKey(
            recId = recId,
            r = r,
            s = s,
            messageHash = messageHash,
            compressed = false
        ) ?: return null
        return pubKeyToAddress(publicKey)
    }

    /** `r‖s‖recId` with the recovery id that recovers [expected], or null when none does. */
    fun messageSignature(messageHash: ByteArray, r: ByteArray, s: ByteArray, expected: Address): ByteArray? {
        val rInt = BigInteger(1, r)
        val sInt = BigInteger(1, s)
        val recId = (0..1).firstOrNull { recoverMessageAddress(messageHash, rInt, sInt, it) == expected }
            ?: return null
        return r + s + byteArrayOf(recId.toByte())
    }

    private fun pubKeyToAddress(publicKey: ByteArray): Address {
        val keyWithoutPrefix = publicKey.copyOfRange(UNCOMPRESSED_KEY_PREFIX_SIZE, publicKey.size)
        return Address(CryptoUtils.sha3(keyWithoutPrefix).copyOfRange(ADDRESS_BYTE_OFFSET, 32))
    }

    /** Brute-forces the recovery id by matching the recovered public key against the expected one. */
    fun findRecoveryId(
        messageHash: ByteArray,
        r: BigInteger,
        s: BigInteger,
        expectedPublicKeyBytes: ByteArray
    ): Int {
        val compressed = isPubKeyCompressed(expectedPublicKeyBytes)
        for (recId in 0..3) {
            val publicKey = tryRecoverPublicKey(recId, r, s, messageHash, compressed)
            if (publicKey != null && publicKey.contentEquals(expectedPublicKeyBytes)) return recId
        }
        return -1
    }

    fun isPubKeyCompressed(encoded: ByteArray): Boolean = when {
        encoded.size == 32 ||
            (encoded.size == 33 && (encoded[0].toInt() == 0x02 || encoded[0].toInt() == 0x03)) -> true

        encoded.size == 65 && encoded[0].toInt() == 0x04 -> false
        else -> throw IllegalArgumentException("Unexpected public key size: ${encoded.size}")
    }

    private fun tryRecoverPublicKey(
        recId: Int,
        r: BigInteger,
        s: BigInteger,
        messageHash: ByteArray,
        compressed: Boolean
    ): ByteArray? {
        if (r < BigInteger.ONE || r >= SECP256K1_ORDER || s < BigInteger.ONE || s >= SECP256K1_ORDER) {
            return null
        }
        val signature = r.toBytes(SIGNATURE_COMPONENT_SIZE) + s.toBytes(SIGNATURE_COMPONENT_SIZE)
        return ECKey.recoverPublicKeyFromSignature(messageHash, signature, recId, compressed)
    }

    private fun recoveryId(v: Int, chainId: Int, gasPrice: GasPrice): Int = when (gasPrice) {
        is GasPrice.Eip1559 -> if (v == 0 || v == 1) v else -1
        is GasPrice.Legacy -> {
            val eip155RecId = v - (EIP155_V_OFFSET + 2 * chainId)
            when {
                eip155RecId == 0 || eip155RecId == 1 -> eip155RecId
                v == PRE_EIP155_V_OFFSET || v == PRE_EIP155_V_OFFSET + 1 -> v - PRE_EIP155_V_OFFSET
                else -> -1
            }
        }
    }
}
