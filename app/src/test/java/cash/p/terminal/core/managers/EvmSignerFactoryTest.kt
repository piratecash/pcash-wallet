package cash.p.terminal.core.managers

import io.horizontalsystems.ethereumkit.core.EthereumKit
import cash.p.terminal.ledger.domain.LedgerAppAccess
import cash.p.terminal.ledger.domain.LedgerRejectedException
import cash.p.terminal.ledger.domain.LedgerSigningFailedException
import cash.p.terminal.ledger.ethereum.LedgerEvmSigner
import cash.p.terminal.ledger.ethereum.LedgerTokenDescriptorStore
import cash.p.terminal.modules.multiswap.providers.backendswap.BackendSwapGoldenVectors
import cash.p.terminal.tangem.common.CustomXPubKeyAddressParser
import cash.p.terminal.tangem.signer.HardwareWalletEvmSigner
import cash.p.terminal.trezor.domain.TrezorCancelledException
import cash.p.terminal.trezor.signer.TrezorEvmSigner
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.trezorkit.client.TrezorClientSession
import cash.p.terminal.trezorkit.client.TrezorMessageSignature
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.hardware.ExternalEvmTransactionSigner
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.GasPrice
import io.horizontalsystems.ethereumkit.models.RawTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

class EvmSignerFactoryTest {

    // Public key metadata matches the depth of the hardware derivation path.
    private val xPubKey =
        "xpub6H3W6JmYJXN4CCKUSnriaiQRCZmG6aq4sCMDqTu1ACyngw7HShf59hAxYjXg" +
                "KDuuHThVEUzdHrc3aXCr9kfvQvZPit5dnD3K9xVRBzjK3rX"

    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage = mockk()
    private val trezorClient: ITrezorClient = mockk()
    private val ledgerAppAccess: LedgerAppAccess = mockk()
    private val factory =
        EvmSignerFactory(hardwarePublicKeyStorage, trezorClient, ledgerAppAccess, mockk<LedgerTokenDescriptorStore>())

    private val hardwareAccount = Account(
        id = "hardware-account-id",
        name = "Tangem",
        type = AccountType.HardwareCard(
            cardId = "card-id",
            backupCardsCount = 0,
            walletPublicKey = "wallet-public-key",
            signedHashes = 0
        ),
        origin = AccountOrigin.Restored,
        level = 0
    )

    private val hardwarePublicKey = HardwarePublicKey(
        accountId = hardwareAccount.id,
        blockchainType = BlockchainType.Ethereum.uid,
        type = HardwarePublicKeyType.PUBLIC_KEY,
        tokenType = TokenType.Native,
        key = SecretString(xPubKey),
        derivationPath = "m/44'/60'/0'/0/0",
        publicKey = byteArrayOf(1, 2, 3),
        derivedPublicKey = byteArrayOf(4, 5, 6)
    )

    // Same xpub (an EVM address is identical across token types) but stored under an ERC-20 token type,
    // representing an account that enabled only a token and therefore has no Native row.
    private val erc20OnlyKey = HardwarePublicKey(
        accountId = hardwareAccount.id,
        blockchainType = BlockchainType.Ethereum.uid,
        type = HardwarePublicKeyType.PUBLIC_KEY,
        tokenType = TokenType.Eip20("0xdac17f958d2ee523a2206206994597c13d831ec7"),
        key = SecretString(xPubKey),
        derivationPath = "m/44'/60'/0'/0/0",
        publicKey = byteArrayOf(1, 2, 3),
        derivedPublicKey = byteArrayOf(4, 5, 6)
    )

    @Before
    fun setUp() {
        EthereumKit.init()
        coEvery {
            hardwarePublicKeyStorage.getKey(hardwareAccount.id, BlockchainType.Ethereum, TokenType.Native)
        } returns hardwarePublicKey
    }

    @Test
    fun createSigner_hardwareCardAccount_usesNativeTokenTypeAndBuildsHardwareSigner() = runBlocking {
        val signer = factory.createSigner(hardwareAccount, BlockchainType.Ethereum, Chain.Ethereum)

        assertTrue(signer is HardwareWalletEvmSigner)
        coVerify(exactly = 1) {
            hardwarePublicKeyStorage.getKey(hardwareAccount.id, BlockchainType.Ethereum, TokenType.Native)
        }
    }

    @Test
    fun resolveAddress_hardwareCardAccount_usesNativeTokenTypeAndParsesAddress() = runBlocking {
        val expectedAddress = Address(CustomXPubKeyAddressParser.parse(xPubKey).addressBytes)

        val address = factory.resolveAddress(hardwareAccount, BlockchainType.Ethereum, Chain.Ethereum)

        assertEquals(expectedAddress, address)
        coVerify(exactly = 1) {
            hardwarePublicKeyStorage.getKey(hardwareAccount.id, BlockchainType.Ethereum, TokenType.Native)
        }
    }

    @Test
    fun resolveAddress_hardwareWithoutNativeKey_fallsBackToAnyBlockchainKey() = runBlocking {
        // Account enabled only an ERC-20 token, so no Native row exists — only a blockchain key.
        coEvery {
            hardwarePublicKeyStorage.getKey(hardwareAccount.id, BlockchainType.Ethereum, TokenType.Native)
        } returns null
        coEvery {
            hardwarePublicKeyStorage.getKeyByBlockchain(hardwareAccount.id, BlockchainType.Ethereum)
        } returns erc20OnlyKey
        val expectedAddress = Address(CustomXPubKeyAddressParser.parse(xPubKey).addressBytes)

        val address = factory.resolveAddress(hardwareAccount, BlockchainType.Ethereum, Chain.Ethereum)

        assertEquals(expectedAddress, address)
    }

    @Test
    fun createSigner_hardwareWithoutNativeKey_fallsBackToAnyBlockchainKey() = runBlocking {
        coEvery {
            hardwarePublicKeyStorage.getKey(hardwareAccount.id, BlockchainType.Ethereum, TokenType.Native)
        } returns null
        coEvery {
            hardwarePublicKeyStorage.getKeyByBlockchain(hardwareAccount.id, BlockchainType.Ethereum)
        } returns erc20OnlyKey

        val signer = factory.createSigner(hardwareAccount, BlockchainType.Ethereum, Chain.Ethereum)

        assertTrue(signer is HardwareWalletEvmSigner)
    }

    @Test
    fun createSigner_mnemonicAccount_buildsBaseSignerWithoutHardwareStorage() = runBlocking {
        val account = mnemonicAccount()

        val signer = factory.createSigner(account, BlockchainType.Ethereum, Chain.Ethereum)

        assertTrue(signer !is HardwareWalletEvmSigner && signer !is TrezorEvmSigner)
        coVerify(exactly = 0) { hardwarePublicKeyStorage.getKey(any(), any(), any()) }
    }

    @Test
    fun resolveAddress_mnemonicAccount_matchesSignerAddress() = runBlocking {
        val account = mnemonicAccount()
        val expectedAddress = Signer.address((account.type as AccountType.Mnemonic).seed, Chain.Ethereum)

        val address = factory.resolveAddress(account, BlockchainType.Ethereum, Chain.Ethereum)

        assertEquals(expectedAddress, address)
    }

    @Test
    fun createSigner_trezorOneAccount_signsTypedDataViaTypedHash() = runBlocking {
        val session = mockk<TrezorClientSession>()
        coEvery { trezorClient.connect<TrezorMessageSignature>(any()) } coAnswers {
            firstArg<suspend TrezorClientSession.() -> TrezorMessageSignature>().invoke(session)
        }
        coEvery { session.signEthereumTypedHash(any(), any(), any()) } throws TrezorCancelledException()
        coEvery {
            hardwarePublicKeyStorage.getKey(trezorOneAccount.id, BlockchainType.Ethereum, TokenType.Native)
        } returns hardwarePublicKey
        val signer = factory.createSigner(trezorOneAccount, BlockchainType.Ethereum, Chain.Ethereum) as TrezorEvmSigner

        assertThrows(TrezorCancelledException::class.java) {
            runBlocking { signer.signTypedDataMessage(BackendSwapGoldenVectors.m1.toTypedDataJson()) }
        }
        coVerify(exactly = 0) { session.signEthereumTypedData(any(), any()) }
    }

    @Test
    fun createSigner_ledgerAccount_everySigningPathReachesTheDevice() = runBlocking<Unit> {
        coEvery {
            hardwarePublicKeyStorage.getKey(ledgerAccount.id, BlockchainType.BinanceSmartChain, TokenType.Native)
        } returns HardwarePublicKey(
            accountId = ledgerAccount.id,
            blockchainType = BlockchainType.BinanceSmartChain.uid,
            type = HardwarePublicKeyType.PUBLIC_KEY,
            tokenType = TokenType.Native,
            key = SecretString(xPubKey),
            derivationPath = "m/44'/60'/0'/0/0",
            publicKey = byteArrayOf(1, 2, 3),
            derivedPublicKey = byteArrayOf(4, 5, 6)
        )
        coEvery { ledgerAppAccess.signWithLedgerApp<Any>(ledgerAccount, any(), any()) } throws LedgerRejectedException()
        val signer = factory.createSigner(ledgerAccount, BlockchainType.BinanceSmartChain, Chain.BinanceSmartChain)
        val transaction = RawTransaction(GasPrice.Legacy(1), 21_000, Address(ByteArray(20)), BigInteger.ONE, 0)

        assertTrue(signer is LedgerEvmSigner && signer is ExternalEvmTransactionSigner)
        val ledgerSigner = signer as LedgerEvmSigner
        assertThrows(LedgerRejectedException::class.java) {
            runBlocking { EvmMessageSigning.signPersonalMessage(ledgerSigner, byteArrayOf(1)) }
        }
        assertThrows(LedgerRejectedException::class.java) {
            runBlocking { EvmMessageSigning.signTypedData(ledgerSigner, BackendSwapGoldenVectors.m1.toTypedDataJson()) }
        }
        assertThrows(LedgerSigningFailedException::class.java) {
            runBlocking { EvmMessageSigning.signLegacyHash(ledgerSigner, ByteArray(32)) }
        }
        assertThrows(LedgerSigningFailedException::class.java) { runBlocking { ledgerSigner.signature(transaction) } }
    }

    private val ledgerAccount = Account(
        id = "ledger-account-id",
        name = "Ledger",
        type = AccountType.LedgerDevice(
            deviceId = "device-id",
            model = "NanoSPlus",
            walletPublicKey = "wallet-public-key",
        ),
        origin = AccountOrigin.Restored,
        level = 0
    )

    private val trezorOneAccount = Account(
        id = "trezor-account-id",
        name = "Trezor",
        type = AccountType.TrezorDevice(
            deviceId = "device-id",
            model = "T1B1",
            firmwareVersion = "1.12.1",
            walletPublicKey = "wallet-public-key",
        ),
        origin = AccountOrigin.Restored,
        level = 0
    )

    private fun mnemonicAccount() = Account(
        id = "mnemonic-account-id",
        name = "Mnemonic",
        type = AccountType.Mnemonic(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about".split(" "),
            ""
        ),
        origin = AccountOrigin.Created,
        level = 0
    )
}
