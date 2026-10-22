package cash.p.terminal.ledger.ethereum

import cash.p.terminal.ledger.domain.LedgerAppAccess
import cash.p.terminal.ledger.ethereum.EvmFixtures.MAIL_JSON
import cash.p.terminal.ledger.ethereum.EvmFixtures.USDT
import cash.p.terminal.ledger.ethereum.EvmFixtures.transfer
import cash.p.terminal.ledger.ethereum.EvmFixtures.usdtTransfer
import cash.p.terminal.ledger.domain.LedgerSigningFailedException
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.crypto.EvmSignatureRecovery
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.hardware.ExternalEvmMessageSigner
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.crypto.EIP712Encoder
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.GasPrice
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LedgerEvmSignerTest {

    private val privateKey = BigInteger(PRIVATE_KEY, 16)
    private val account = mockk<Account> {
        every { id } returns ACCOUNT_ID
        every { type } returns AccountType.LedgerDevice("device", "NanoSPlus", "wallet-key")
        every { isHardwareWalletAccount } returns true
    }
    private val descriptors = LedgerTokenDescriptorStore(InMemoryPreferences())

    @Test
    fun signTransaction_eip1559_carriesParityAndRecoversTheAccount() = runTest {
        val device = FakeEthereumApp(privateKey, Chain.Ethereum.id)
        val transaction = transfer(GasPrice.Eip1559(30_000_000_000, 1_000_000_000))

        val signed = signer(device, Chain.Ethereum).signTransaction(transaction)

        assertTrue(signed.signature.v in 0..1)
        assertEquals(device.address, EvmSignatureRecovery.recoverSenderAddress(transaction, signed.signature, Chain.Ethereum.id))
    }

    @Test
    fun signTransaction_legacyOnEveryChain_carriesFullEip155V() = runTest {
        listOf(Chain.BinanceSmartChain, Chain.Avalanche, Chain.RobinhoodChain, Chain.ZkSync).forEach { chain ->
            val device = FakeEthereumApp(privateKey, chain.id)
            val transaction = transfer(GasPrice.Legacy(5_000_000_000))

            val signature = signer(device, chain).signTransaction(transaction).signature

            assertTrue(signature.v - (chain.id * 2 + 35) in 0..1, "chain ${chain.id}: v ${signature.v}")
            assertEquals(device.address, EvmSignatureRecovery.recoverSenderAddress(transaction, signature, chain.id))
        }
    }

    @Test
    fun signTransaction_tokenWithCachedDescriptor_providesItBeforeSigning() = runTest {
        val device = FakeEthereumApp(privateKey, Chain.Ethereum.id)
        descriptors.save(Chain.Ethereum.id, USDT, byteArrayOf(1, 2, 3))

        signer(device, Chain.Ethereum).signTransaction(usdtTransfer(GasPrice.Legacy(1)))

        assertEquals(listOf(PROVIDE_ERC20, SIGN_TRANSACTION), device.instructions.takeLast(2))
    }

    @Test
    fun signTransaction_tokenCachedOnlyForAnotherChain_signsBlind() = runTest {
        val device = FakeEthereumApp(privateKey, Chain.Ethereum.id)
        descriptors.save(Chain.BinanceSmartChain.id, USDT, byteArrayOf(1, 2, 3))

        signer(device, Chain.Ethereum).signTransaction(usdtTransfer(GasPrice.Legacy(1)))

        assertEquals(listOf(SIGN_TRANSACTION), device.instructions.filter { it in setOf(PROVIDE_ERC20, SIGN_TRANSACTION) })
    }

    @Test
    fun signTransaction_deviceSignsWithAnotherAccount_throwsSigningFailed() = runTest {
        val otherDevice = FakeEthereumApp(privateKey + BigInteger.ONE, Chain.Ethereum.id)

        assertFailsWith<LedgerSigningFailedException> {
            signer(otherDevice, Chain.Ethereum, address = FakeEthereumApp(privateKey, 1).address)
                .signTransaction(transfer(GasPrice.Legacy(1)))
        }
    }

    @Test
    fun signPersonalMessage_deviceSignature_recoversTheAccount() = runTest {
        val device = FakeEthereumApp(privateKey, Chain.Ethereum.id)
        val message = "Sign in to the swap service".encodeToByteArray()

        val signature = signer(device, Chain.Ethereum).signPersonalMessage(message)

        assertRecovers(device.address, EvmSignatureRecovery.personalSignHash(message), signature)
    }

    @Test
    fun signTypedDataMessage_deviceSignature_recoversTheAccount() = runTest {
        val digest = EIP712Encoder().encodeTypedDataHash(MAIL_JSON)
        val device = FakeEthereumApp(privateKey, Chain.Ethereum.id, typedDataHash = digest)

        val signature = signer(device, Chain.Ethereum).signTypedDataMessage(MAIL_JSON)

        assertRecovers(device.address, digest, signature)
    }

    @Test
    fun signature_ledgerSigner_neverSignsWithTheMockKey() = runTest {
        val signer = signer(FakeEthereumApp(privateKey, Chain.Ethereum.id), Chain.Ethereum)

        assertTrue(signer is ExternalEvmMessageSigner)
        assertFailsWith<LedgerSigningFailedException> { signer.signature(transfer(GasPrice.Legacy(1))) }
        assertFailsWith<LedgerSigningFailedException> { signer.signLegacyHash(ByteArray(32)) }
    }

    private fun assertRecovers(expected: Address, hash: ByteArray, signature: ByteArray) {
        val r = BigInteger(1, signature.copyOfRange(0, 32))
        val s = BigInteger(1, signature.copyOfRange(32, 64))
        assertEquals(expected, EvmSignatureRecovery.recoverMessageAddress(hash, r, s, signature[64].toInt()))
    }

    private fun signer(device: FakeEthereumApp, chain: Chain, address: Address = device.address): LedgerEvmSigner {
        val access = mockk<LedgerAppAccess> {
            coEvery { signWithLedgerApp<Any>(account, any(), any()) } coAnswers {
                thirdArg<suspend (LedgerTransport) -> Any>().invoke(device)
            }
        }
        return LedgerEvmSigner(access, descriptors, account, storedKey(), address, chain)
    }

    private fun storedKey() = HardwarePublicKey(
        accountId = ACCOUNT_ID,
        blockchainType = BlockchainType.Ethereum.uid,
        type = HardwarePublicKeyType.PUBLIC_KEY,
        tokenType = TokenType.Native,
        key = SecretString("xpub"),
        derivationPath = "m/44'/60'/0'/0/0",
        publicKey = byteArrayOf(1),
        derivedPublicKey = byteArrayOf(1),
    )

    private companion object {
        const val ACCOUNT_ID = "ledger-account"
        const val PRIVATE_KEY = "4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318"
        const val PROVIDE_ERC20 = 0x0A
        const val SIGN_TRANSACTION = 0x04
    }
}
