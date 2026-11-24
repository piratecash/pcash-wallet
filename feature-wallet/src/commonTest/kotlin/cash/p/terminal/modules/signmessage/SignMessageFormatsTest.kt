package cash.p.terminal.modules.signmessage

import cash.p.terminal.core.purpose
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.TokenType.Derivation
import io.horizontalsystems.bitcoincore.core.IStorage
import io.horizontalsystems.bitcoincore.core.Wallet
import io.horizontalsystems.bitcoincore.core.scriptType
import io.horizontalsystems.bitcoincore.messages.MessageSignatureFormat.Bip322
import io.horizontalsystems.bitcoincore.messages.MessageSignatureFormat.Standard
import io.horizontalsystems.bitcoincore.messages.MessageSigner
import io.horizontalsystems.bitcoincore.models.PublicKey
import io.horizontalsystems.bitcoincore.network.Network
import io.horizontalsystems.bitcoincore.serializers.BaseTransactionSerializer
import io.horizontalsystems.bitcoincore.transactions.builder.EcdsaInputSigner
import io.horizontalsystems.bitcoincore.transactions.builder.SchnorrInputSigner
import io.horizontalsystems.bitcoincore.transactions.scripts.Sighash
import io.horizontalsystems.bitcoincore.utils.AddressConverterChain
import io.horizontalsystems.bitcoincore.utils.Base58AddressConverter
import io.horizontalsystems.bitcoincore.utils.SegwitAddressConverter
import io.horizontalsystems.hdwalletkit.HDWallet
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignMessageFormatsTest {

    private val software = AccountType.Mnemonic(MNEMONIC.split(" "), "")
    private val trezor = AccountType.TrezorDevice("device", "T3T1", "2.8.0", "xpub")

    @Test
    fun available_softwareAccount_matchesFormatTable() {
        assertEquals(listOf(Standard), SignMessageFormats.available(software, Derivation.Bip44))
        assertEquals(listOf(Standard), SignMessageFormats.available(software, Derivation.Bip49))
        assertEquals(listOf(Standard, Bip322), SignMessageFormats.available(software, Derivation.Bip84))
        assertEquals(listOf(Bip322), SignMessageFormats.available(software, Derivation.Bip86))
    }

    @Test
    fun available_trezorAccount_standardOnlyAndNothingForTaproot() {
        assertEquals(listOf(Standard), SignMessageFormats.available(trezor, Derivation.Bip44))
        assertEquals(listOf(Standard), SignMessageFormats.available(trezor, Derivation.Bip49))
        assertEquals(listOf(Standard), SignMessageFormats.available(trezor, Derivation.Bip84))
        assertEquals(emptyList(), SignMessageFormats.available(trezor, Derivation.Bip86))
    }

    @Test
    fun available_ledgerAccount_standardOnlyAndNothingForTaproot() {
        val ledger = AccountType.LedgerDevice("ledger-id", "nanoSP", "wallet-key")

        assertEquals(listOf(Standard), SignMessageFormats.available(ledger, Derivation.Bip44))
        assertEquals(listOf(Standard), SignMessageFormats.available(ledger, Derivation.Bip49))
        assertEquals(listOf(Standard), SignMessageFormats.available(ledger, Derivation.Bip84))
        assertEquals(emptyList(), SignMessageFormats.available(ledger, Derivation.Bip86))
    }

    @Test
    fun available_everyOfferedFormat_signsWithSoftwareKit() = runTest {
        val seed = Mnemonic().toSeed(MNEMONIC.split(" "))
        Derivation.entries.forEach { derivation ->
            val purpose = derivation.purpose
            val wallet = Wallet(HDWallet(seed, 0, purpose), 20)
            val publicKey = wallet.publicKey(0, 0, true)
            val address = addressConverter.convert(publicKey, purpose.scriptType).stringValue
            val serializer = BaseTransactionSerializer()
            val signer = MessageSigner(
                EcdsaInputSigner(wallet, serializer, network),
                SchnorrInputSigner(wallet, serializer),
                storageWith(publicKey),
                addressConverter,
                purpose.scriptType,
            )

            SignMessageFormats.available(software, derivation).forEach { format ->
                assertTrue(signer.sign(address, "hello", format).isNotEmpty(), "$derivation $format")
            }
        }
    }

    private val addressConverter = AddressConverterChain().apply {
        prependConverter(SegwitAddressConverter("bc"))
        prependConverter(Base58AddressConverter(0, 5))
    }

    private val network: Network = mockk {
        every { sigHashValue } returns Sighash.ALL
        every { sigHashForked } returns false
    }

    private fun storageWith(publicKey: PublicKey): IStorage = mockk {
        every { getPublicKeyByKeyOrKeyHash(publicKey.publicKeyHash) } returns publicKey
        every { getPublicKeyByScriptHashForP2PWKH(publicKey.scriptHashP2WPKH) } returns publicKey
        every { getPublicKeyByHashP2TR(publicKey.convertedForP2TR) } returns publicKey
    }

    private companion object {
        const val MNEMONIC = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    }
}
