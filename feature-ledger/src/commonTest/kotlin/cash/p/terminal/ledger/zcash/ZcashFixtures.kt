package cash.p.terminal.ledger.zcash

import cash.p.terminal.ledgerkit.apdu.StatusWord
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType

/** Account 0 of the speculos seed, as the Zcash app answers (app-zcash test vectors, read back on speculos). */
object ZcashFixtures {
    val transparent = TokenQuery(BlockchainType.Zcash, TokenType.AddressSpecTyped(TokenType.AddressSpecType.Transparent))
    val unified = TokenQuery(BlockchainType.Zcash, TokenType.AddressSpecTyped(TokenType.AddressSpecType.Unified))

    const val DEVICE_UFVK =
        "uview1zkk7f8hp2m5v09kq7h29vkgngwhhvgy2ey32cy5j0kp69g7ju2vqjvnue03u99z382rtkgvj3f8vtqdtxfxvgjytezgt39dqc0" +
            "lyt2sj084jdq4md69snc3wxdcl8uah8sxw3rrt9pnxnfl3r4xnczapts7gr4l0cuell7dcjv36gkdcsl4axps827xt6fgmfl78zlh" +
            "ddec72tn2p0eqnpkuy7a08puhj97v0ahxuqlyzmyqtldqnc0p3696d9ww8x6mpd56mz6w32twryevru2rx34lf8dtqsp50gar"
    const val DEVICE_ORCHARD_ADDRESS =
        "u1u2h4ce7e2cn3z4nzur95muq2dl4da9x8h8kdp2l80gm9nl9raj8zzpx79ycjnfvar4v5exea5pqr5y9qsnlp0cdunwf9yjjx5c4q7ar9"

    /** `m/44'/133'/0'`: uncompressed key and chain code. */
    const val ACCOUNT_KEY =
        "04ea46e12e4cfa2d5e502f311aba6fee8c8d72098888496b6e405840d74d22e758a0cecc8ebfbb3c9c44542297d8ce2a9bbdf455b8" +
            "63ed1b847ace8ec46dc54762"
    const val ACCOUNT_KEY_COMPRESSED = "02ea46e12e4cfa2d5e502f311aba6fee8c8d72098888496b6e405840d74d22e758"
    const val ACCOUNT_CHAIN_CODE = "d03db42b5050a71d7c800af0c37b9350eb2086ce7d17237c0a7f31dcb8f3f70b"

    /** `m/44'/133'/0'/0/7`. */
    const val ADDRESS_7 = "t1gKVYSH9sh8JhfK9bQu8gsa4mKQTzUGWzA"
}

/**
 * The Zcash app with fixed answers: the account key for a three-level `GET_WALLET_PUBLIC_KEY`, [address] for a deeper
 * one, the fixtures' viewing key and unified address, and [answers] for the PCZT commands, keyed by INS; [statuses]
 * replaces the answer of an INS with a status word.
 */
class FakeZcashApp(
    private val address: String = ZcashFixtures.ADDRESS_7,
    private val unifiedAddress: String = ZcashFixtures.DEVICE_ORCHARD_ADDRESS,
    private val answers: Map<Int, ByteArray> = emptyMap(),
    private val statuses: Map<Int, Int> = emptyMap(),
) : LedgerTransport {
    val apdus = mutableListOf<ByteArray>()

    fun sent(ins: Int): List<ByteArray> = apdus.filter { it[INS].toInt() and 0xFF == ins }

    override suspend fun exchange(apdu: ByteArray): ByteArray {
        apdus += apdu
        statuses[apdu[INS].toInt() and 0xFF]?.let { return statusBytes(it) }
        val answer = when (apdu[INS].toInt() and 0xFF) {
            GET_WALLET_PUBLIC_KEY -> walletPublicKey(isAccount = apdu[PATH_DEPTH].toInt() == ACCOUNT_DEPTH)
            GET_VK -> lengthPrefixed(ZcashFixtures.DEVICE_UFVK)
            GET_SHIELD_ADDR -> lengthPrefixed(unifiedAddress)
            else -> answers[apdu[INS].toInt() and 0xFF] ?: ByteArray(0)
        }
        return answer + statusBytes(StatusWord.OK)
    }

    private fun statusBytes(statusWord: Int) = byteArrayOf((statusWord shr 8).toByte(), statusWord.toByte())

    override fun close() = Unit

    private fun walletPublicKey(isAccount: Boolean): ByteArray {
        val key = ZcashFixtures.ACCOUNT_KEY.hexToByteArray()
        val shown = (if (isAccount) "t1accountaddress" else address).encodeToByteArray()
        val chainCode = ZcashFixtures.ACCOUNT_CHAIN_CODE.hexToByteArray()
        return byteArrayOf(key.size.toByte()) + key + byteArrayOf(shown.size.toByte()) + shown + chainCode
    }

    private fun lengthPrefixed(text: String): ByteArray {
        val bytes = text.encodeToByteArray()
        return byteArrayOf((bytes.size shr 8).toByte(), bytes.size.toByte()) + bytes
    }

    companion object {
        const val GET_WALLET_PUBLIC_KEY = 0x40
        const val GET_VK = 0x50
        const val GET_SHIELD_ADDR = 0x51
        const val INS = 1
        const val P1 = 2
        const val PATH_DEPTH = 5
        const val ACCOUNT_DEPTH = 3
    }
}
