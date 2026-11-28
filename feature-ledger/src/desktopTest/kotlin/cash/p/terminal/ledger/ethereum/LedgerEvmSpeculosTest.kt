package cash.p.terminal.ledger.ethereum

import cash.p.terminal.ledger.Speculos
import cash.p.terminal.ledger.domain.LedgerUserOperations
import cash.p.terminal.ledger.ethereum.EvmFixtures.MAIL_JSON
import cash.p.terminal.ledger.ethereum.EvmFixtures.hex
import cash.p.terminal.ledger.ethereum.EvmFixtures.transfer
import cash.p.terminal.ledger.ethereum.EvmFixtures.usdtTransfer
import cash.p.terminal.ledger.domain.LedgerAppAccess
import cash.p.terminal.ledger.domain.LedgerAppKeyFetchers
import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.PublicHDExtendedKeyParser
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.core.signer.Signer
import io.horizontalsystems.ethereumkit.crypto.InternalBouncyCastleProvider
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.Chain
import io.horizontalsystems.ethereumkit.models.GasPrice
import io.horizontalsystems.ethereumkit.models.RawTransaction
import io.horizontalsystems.hdwalletkit.HDKeychain
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.security.Security
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Needs speculos with the Ethereum app; see [Speculos]. The app signs with RFC 6979 nonces, as the kit's
 * software signer does, so each device signature must equal the one derived from the speculos seed.
 */
class LedgerEvmSpeculosTest {

    private val seed = Mnemonic().toSeed(Speculos.SEED.split(" "))

    @BeforeTest
    fun setUp() {
        Security.addProvider(InternalBouncyCastleProvider.getInstance())
    }

    @Test
    fun fetchKey_ethereumApp_matchesKeyDerivedFromSeed() = runBlocking {
        Speculos.withSession(ETHEREUM_APP) { session ->
            val storedKey = fetchStoredKey(session, ethereum)

            assertContentEquals(
                HDKeychain(seed).getKeyByPath(PATH).pubKey,
                PublicHDExtendedKeyParser.parse(storedKey.key.value).key.pubKey,
            )
            assertEquals(Address(SPECULOS_ADDRESS), Signer.address(seed, Chain.Ethereum))
        }
    }

    // The app refuses a display it cannot complete, so returning proves the user saw the address.
    @Test
    fun verify_bscAddress_isConfirmedOnDevice() = runBlocking {
        val wallet = mockk<Wallet> { every { token.tokenQuery } returns bsc }
        every { wallet.token.blockchainType } returns BlockchainType.BinanceSmartChain

        Speculos.withSession(ETHEREUM_APP) { session ->
            Speculos.approving {
                session.withApp(ETHEREUM_APP, "0") { LedgerEvmAddressVerifier().verify(it, wallet, SPECULOS_ADDRESS) }
            }
        }
    }

    @Test
    fun signTransaction_ethereumEip1559Transfer_matchesSeedSignature() = runBlocking {
        assertSignsLikeSeed(Chain.Ethereum, ethereum, transfer(GasPrice.Eip1559(2_000_000_000, 1_000_000_000)))
    }

    @Test
    fun signTransaction_bscLegacyTransfer_matchesSeedSignature() = runBlocking {
        assertSignsLikeSeed(Chain.BinanceSmartChain, bsc, transfer(GasPrice.Legacy(5_000_000_000)))
    }

    @Test
    fun signTransaction_erc20TransferBlindSigningOn_matchesSeedSignature() = runBlocking {
        val transaction = usdtTransfer(GasPrice.Eip1559(2_000_000_000, 1_000_000_000))
        Speculos.withSession(ETHEREUM_APP) { session ->
            setBlindSigning(session, enabled = true)
            assertSignsLikeSeed(session, Chain.Ethereum, ethereum, transaction)
        }
    }

    @Test
    fun signPersonalMessage_viaLedgerSigner_matchesSeedSignature() = runBlocking {
        val message = "Sign in to the swap service".encodeToByteArray()
        Speculos.withSession(ETHEREUM_APP) { session ->
            val signature = Speculos.approving { signer(session, Chain.Ethereum, ethereum).signPersonalMessage(message) }

            assertContentEquals(Signer.getInstance(seed, Chain.Ethereum).signByteArray(message), signature)
        }
    }

    @Test
    fun signTypedDataMessage_viaLedgerSigner_matchesSeedSignature() = runBlocking {
        Speculos.withSession(ETHEREUM_APP) { session ->
            setBlindSigning(session, enabled = true)
            val signature = Speculos.approving { signer(session, Chain.Ethereum, ethereum).signTypedDataMessage(MAIL_JSON) }

            assertContentEquals(Signer.getInstance(seed, Chain.Ethereum).signTypedData(MAIL_JSON), signature)
        }
    }

    private suspend fun assertSignsLikeSeed(chain: Chain, query: TokenQuery, transaction: RawTransaction) =
        Speculos.withSession(ETHEREUM_APP) { session -> assertSignsLikeSeed(session, chain, query, transaction) }

    private suspend fun assertSignsLikeSeed(
        session: LedgerSession,
        chain: Chain,
        query: TokenQuery,
        transaction: RawTransaction,
    ) {
        val signed = Speculos.approving { signer(session, chain, query).signTransaction(transaction) }

        val expected = Signer.getInstance(seed, chain).signature(transaction)
        assertEquals(expected.v, signed.signature.v)
        assertContentEquals(expected.r, signed.signature.r)
        assertContentEquals(expected.s, signed.signature.s)
    }

    private suspend fun signer(session: LedgerSession, chain: Chain, query: TokenQuery): LedgerEvmSigner {
        val storedKey = fetchStoredKey(session, query)
        val storage = mockk<IHardwarePublicKeyStorage> {
            coEvery { getKey(ACCOUNT_ID, query.blockchainType, query.tokenType) } returns storedKey
        }
        val account = mockk<Account> {
            every { id } returns ACCOUNT_ID
            every { type } returns AccountType.LedgerDevice("speculos", "NanoSPlus", storedKey.key.value)
            every { isHardwareWalletAccount } returns true
        }
        val access = LedgerAppAccess(
            session,
            LedgerAppKeyFetchers(listOf(LedgerEvmKeyFetcher())),
            storage,
            LedgerUserOperations(),
        )
        val descriptors = LedgerTokenDescriptorStore(InMemoryPreferences())
        return LedgerEvmSigner(access, descriptors, account, storedKey, Signer.address(seed, chain), chain)
    }

    private suspend fun fetchStoredKey(session: LedgerSession, query: TokenQuery): HardwarePublicKey =
        session.withApp(ETHEREUM_APP, "0") { LedgerEvmKeyFetcher().fetchKey(it, query) }.toHardwarePublicKey(ACCOUNT_ID)

    /** Flips the app's blind signing setting through its menu when it differs from [enabled]. */
    private suspend fun setBlindSigning(session: LedgerSession, enabled: Boolean) {
        val flags = session.withApp(ETHEREUM_APP, "0") { it.exchange(hex(GET_APP_CONFIGURATION)) }[0].toInt()
        if ((flags and BLIND_SIGNING_FLAG != 0) != enabled) Speculos.toggleSetting("Blind signing")
    }

    private companion object {
        const val ACCOUNT_ID = "speculos-ledger"
        const val PATH = "m/44'/60'/0'/0/0"
        const val SPECULOS_ADDRESS = "0xDad77910DbDFdE764fC21FCD4E74D71bBACA6D8D"
        const val GET_APP_CONFIGURATION = "e006000000"
        const val BLIND_SIGNING_FLAG = 0x01
        val ethereum = TokenQuery(BlockchainType.Ethereum, TokenType.Native)
        val bsc = TokenQuery(BlockchainType.BinanceSmartChain, TokenType.Native)
    }
}
