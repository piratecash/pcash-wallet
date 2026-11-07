package cash.p.terminal.core.managers

import io.horizontalsystems.ethereumkit.core.EthereumKit
import cash.p.terminal.tangem.signer.HardwareWalletEvmSigner
import cash.p.terminal.trezor.domain.TrezorSigningException
import cash.p.terminal.trezor.signer.TrezorEvmSigner
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.models.Chain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class EvmMessageSigningTest {

    @Before
    fun setUp() {
        EthereumKit.init()
    }

    private fun mnemonicSigner(): Signer {
        val words =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" ")
        val seed = AccountType.Mnemonic(words, "").seed
        return Signer.getInstance(seed, Chain.Ethereum)
    }

    @Test
    fun signPersonalMessage_mnemonicSigner_matchesSignByteArray() = runBlocking {
        val signer = mnemonicSigner()
        val message = "hello".toByteArray()

        val expected = signer.signByteArray(message)
        val actual = EvmMessageSigning.signPersonalMessage(signer, message)

        assertArrayEquals(expected, actual)
    }

    @Test
    fun signLegacyHash_mnemonicSigner_matchesSignByteArrayLegacy() = runBlocking {
        val signer = mnemonicSigner()
        val hash = ByteArray(32) { it.toByte() }

        val expected = signer.signByteArrayLegacy(hash)
        val actual = EvmMessageSigning.signLegacyHash(signer, hash)

        assertArrayEquals(expected, actual)
    }

    @Test
    fun signPersonalMessage_trezorSigner_routesToDeviceSigning() = runBlocking {
        val signer = mockk<TrezorEvmSigner>()
        val message = "hello".toByteArray()
        coEvery { signer.signPersonalMessage(message) } returns DEVICE_SIGNATURE

        assertArrayEquals(DEVICE_SIGNATURE, EvmMessageSigning.signPersonalMessage(signer, message))
    }

    @Test
    fun signTypedData_trezorSigner_routesToDeviceSigning() = runBlocking {
        val signer = mockk<TrezorEvmSigner>()
        coEvery { signer.signTypedDataMessage(TYPED_DATA) } returns DEVICE_SIGNATURE

        assertArrayEquals(DEVICE_SIGNATURE, EvmMessageSigning.signTypedData(signer, TYPED_DATA))
    }

    @Test
    fun signPersonalMessage_tangemSigner_routesToCardSigning() = runBlocking {
        val signer = mockk<HardwareWalletEvmSigner>()
        val message = "hello".toByteArray()
        coEvery { signer.signPersonalMessage(message) } returns DEVICE_SIGNATURE

        assertArrayEquals(DEVICE_SIGNATURE, EvmMessageSigning.signPersonalMessage(signer, message))
    }

    @Test
    fun signLegacyHash_tangemSigner_routesToCardSigning() = runBlocking {
        val signer = mockk<HardwareWalletEvmSigner>()
        val hash = ByteArray(32)
        coEvery { signer.signLegacyHash(hash) } returns DEVICE_SIGNATURE

        assertArrayEquals(DEVICE_SIGNATURE, EvmMessageSigning.signLegacyHash(signer, hash))
    }

    @Test
    fun signTypedData_tangemSigner_routesToCardSigning() = runBlocking {
        val signer = mockk<HardwareWalletEvmSigner>()
        coEvery { signer.signTypedDataMessage(TYPED_DATA) } returns DEVICE_SIGNATURE

        assertArrayEquals(DEVICE_SIGNATURE, EvmMessageSigning.signTypedData(signer, TYPED_DATA))
    }

    @Test
    fun signLegacyHash_trezorSignerNotSupported_propagatesException() {
        val signer = mockk<TrezorEvmSigner>()
        val hash = ByteArray(32)
        coEvery { signer.signLegacyHash(hash) } throws TrezorSigningException("eth_sign is not supported on Trezor")

        assertThrows(TrezorSigningException::class.java) {
            runBlocking { EvmMessageSigning.signLegacyHash(signer, hash) }
        }
    }

    private companion object {
        val DEVICE_SIGNATURE = ByteArray(65) { 7 }
        const val TYPED_DATA = "{}"
    }
}
