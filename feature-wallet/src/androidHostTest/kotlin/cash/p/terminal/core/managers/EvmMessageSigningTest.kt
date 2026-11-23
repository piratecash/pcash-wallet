package cash.p.terminal.core.managers

import io.horizontalsystems.ethereumkit.core.EthereumKit
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.models.Chain
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
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
}
