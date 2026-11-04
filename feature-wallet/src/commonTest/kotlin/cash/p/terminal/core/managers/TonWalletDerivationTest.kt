package cash.p.terminal.core.managers

import cash.p.terminal.core.toFixedSize
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.TonWalletVersion
import io.horizontalsystems.tonkit.core.TonWallet
import io.horizontalsystems.tonkit.models.Network
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

class TonWalletDerivationTest {

    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage = mockk()

    private fun createMnemonicAccount(words: List<String>): Account {
        return Account(
            id = "test",
            name = "test",
            type = AccountType.Mnemonic(words, ""),
            origin = AccountOrigin.Restored,
            level = 0
        )
    }

    @Test
    fun toTonWallet_mnemonicWith31BytePrivateKey_createsSeedSuccessfully() {
        // This mnemonic produces a 31-byte private key (leading 0x00 stripped by BigInteger)
        val words = "disease other myth twist flip law dice layer notice door staff require"
            .split(" ")

        val account = createMnemonicAccount(words)
        val tonWallet = account.toTonWallet(TonWalletVersion.V4R2, hardwarePublicKeyStorage, null)

        assert(tonWallet is TonWallet.Seed)
    }

    @Test
    fun toTonWallet_normalMnemonic_createsSeedSuccessfully() {
        val words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
            .split(" ")

        val account = createMnemonicAccount(words)
        val tonWallet = account.toTonWallet(TonWalletVersion.V4R2, hardwarePublicKeyStorage, null)

        assert(tonWallet is TonWallet.Seed)
    }

    // Vectors from @ton/ton (WalletContractV4 / WalletContractV5R1) for the BIP44 key m/44'/607'/0' of
    // this mnemonic, generated the same way as the kit's tools/w5-vectors.
    @Test
    fun toTonWallet_bothVersions_addressesMatchReferenceImplementation() {
        val words = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
            .split(" ")
        val account = createMnemonicAccount(words)

        val v4r2 = account.toTonWallet(TonWalletVersion.V4R2, hardwarePublicKeyStorage, null)
        val w5 = account.toTonWallet(TonWalletVersion.W5, hardwarePublicKeyStorage, null)

        assertEquals(V4R2_ADDRESS, v4r2.address(Network.MainNet).toRaw())
        assertEquals(W5_ADDRESS, w5.address(Network.MainNet).toRaw())
    }

    @Test
    fun toTonWallet_watchAddressW5_throws() {
        val account = Account(
            id = "test",
            name = "test",
            type = AccountType.TonAddress("UQD5mxRgCuRNLxKxeOjG6r14iSroLF5FtomPnet-sgP5xNJb"),
            origin = AccountOrigin.Restored,
            level = 0
        )

        assertFailsWith<IllegalArgumentException> {
            account.toTonWallet(TonWalletVersion.W5, hardwarePublicKeyStorage, null)
        }
    }

    @Test
    fun toFixedSize_shortKey_padsWithLeadingZeros() {
        val shortKey = ByteArray(31) { (it + 1).toByte() }
        val result = shortKey.toFixedSize(32)

        assertEquals(32, result.size)
        assertEquals(0.toByte(), result[0])
        assertEquals(1.toByte(), result[1])
        assertEquals(31.toByte(), result[31])
    }

    @Test
    fun toFixedSize_longKey_stripsLeadingSignByte() {
        val longKey = ByteArray(33) { it.toByte() } // [0x00, 0x01, ..., 0x20]
        val result = longKey.toFixedSize(32)

        assertEquals(32, result.size)
        assertEquals(1.toByte(), result[0])
        assertEquals(32.toByte(), result[31])
    }

    @Test
    fun toFixedSize_exactSize_returnsUnchanged() {
        val key = ByteArray(32) { it.toByte() }
        val result = key.toFixedSize(32)

        assertEquals(32, result.size)
        assert(key.contentEquals(result))
    }

    private companion object {
        const val V4R2_ADDRESS = "0:335996ba9cce6625ebfdd7019ced50927c1a08b1846a289a55efc37c01d77df9"
        const val W5_ADDRESS = "0:47caefa86550c7458435fab2a51aaa47cb9c80a9a9cf062ac440d30d3a67c72e"
    }
}
