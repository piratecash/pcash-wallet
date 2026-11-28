package cash.p.terminal.ledger.zcash

import cash.p.terminal.ledger.Speculos
import cash.p.terminal.ledger.account.AccountFixtures
import cash.p.terminal.ledger.account.AccountSpeculos
import cash.p.terminal.ledger.domain.LedgerAppAccess
import cash.p.terminal.ledger.domain.LedgerAppKeyFetchers
import cash.p.terminal.ledger.domain.LedgerAppRequirements
import cash.p.terminal.ledger.domain.LedgerKey
import cash.p.terminal.ledger.domain.LedgerSigningKey
import cash.p.terminal.ledger.domain.LedgerUserOperations
import cash.p.terminal.ledger.zcash.ZcashFixtures.DEVICE_ORCHARD_ADDRESS
import cash.p.terminal.ledger.zcash.ZcashFixtures.DEVICE_UFVK
import cash.p.terminal.ledger.zcash.ZcashFixtures.transparent
import cash.p.terminal.ledger.zcash.ZcashFixtures.unified
import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.ledgerkit.bitcoin.BtchipLegacyClient
import cash.p.terminal.ledgerkit.zcash.ZcashAppClient
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.hardware.HardwareAddressLocator
import cash.p.terminal.wallet.hardware.HardwareAddressPosition
import cash.p.zcash.PreparedTransaction
import cash.p.zcash.ServerConfig
import cash.p.zcash.ZcashNetwork
import cash.p.zcash.ZcashSdk
import cash.p.zcash.ZcashWallet
import cash.p.zcash.deriveAddresses
import cash.p.zcash.deriveAddressesFromKey
import cash.p.zcash.transactionId
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.hdwalletkit.HDKeychain
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Needs speculos with the Zcash app; see [Speculos]. Keys are checked against app-zcash's own vectors and the SDK's
 * derivation from the seed; PCZTs are the SDK's planned V5/V6 fixtures of that seed, signed here and extracted.
 */
class LedgerZcashSpeculosTest {

    private val fetcher = LedgerZcashKeyFetcher()

    @Test
    fun fetchKeys_zcashApp_viewingKeyAndAccountKeyOfSeed() = withZcash { session ->
        val keys = readKeys(session)

        assertTrue(keys.all { it.key == DEVICE_UFVK })
        val seedAccountKey = HDKeychain(AccountSpeculos.seed).getKeyByPath(ACCOUNT_PATH).pubKey
        assertTrue(keys.all { it.publicKey.contentEquals(seedAccountKey) })
        val fromSeed = ZcashSdk.deriveAddresses(Speculos.SEED, ZcashNetwork.MAIN)
        val fromViewingKey = ZcashSdk.deriveAddressesFromKey(DEVICE_UFVK, ZcashNetwork.MAIN)
        assertEquals(fromSeed.transparent, fromViewingKey.transparent)
        assertEquals(DEVICE_ORCHARD_ADDRESS, fromViewingKey.orchard)
    }

    @Test
    fun restoreAccount_deviceViewingKey_watchesTransparentAndOrchardOfDevice() = withZcash { _ ->
        withWallet { wallet ->
            val id = wallet.restoreAccount("ledger", DEVICE_UFVK)
            val addresses = wallet.addresses(id)

            assertEquals(DEVICE_ORCHARD_ADDRESS, addresses.orchard)
            assertEquals(ZcashSdk.deriveAddressesFromKey(DEVICE_UFVK, ZcashNetwork.MAIN).transparent, addresses.transparent)
            assertEquals(null, addresses.sapling)
        }
    }

    // No approval walker runs: an answer at all proves the identity check never waits for the user.
    @Test
    fun holdsKey_zcashApp_answersWithoutUserAction() = withZcash { session ->
        val seedAccountKey = HDKeychain(AccountSpeculos.seed).getKeyByPath(ACCOUNT_PATH).pubKey

        session.withApp(LedgerAppRequirements.ZCASH_APP, "0") { transport ->
            assertTrue(fetcher.holdsKey(transport, unified, DEVICE_UFVK, seedAccountKey))
            assertFalse(fetcher.holdsKey(transport, unified, DEVICE_UFVK, seedAccountKey.reversedArray()))
        }
    }

    @Test
    fun verify_unifiedReceiveAddress_confirmedOnDevice() = withZcash { session ->
        verify(session, unified, DEVICE_ORCHARD_ADDRESS)

        val shown = session.withApp(LedgerAppRequirements.ZCASH_APP, "0") {
            ZcashAppClient(it).getUnifiedAddress(LEDGER_ZCASH_ACCOUNT)
        }
        assertEquals(DEVICE_ORCHARD_ADDRESS, shown)
    }

    @Test
    fun verify_freshTransparentAddress_confirmedOnDeviceAtItsIndex() = withZcash { session ->
        val first = session.withApp(LedgerAppRequirements.ZCASH_APP, "0") {
            BtchipLegacyClient(it).getWalletPublicKey(DerivationPath.parse("$ACCOUNT_PATH/0/0")).address
        }
        assertEquals(ZcashSdk.deriveAddresses(Speculos.SEED, ZcashNetwork.MAIN).transparent, first)

        withWallet { wallet ->
            val id = wallet.restoreAccount("ledger", DEVICE_UFVK)
            val fresh = List(FRESH_COUNT) { checkNotNull(wallet.nextTransparentAddress(id)) }.last()
            assertTrue(checkNotNull(wallet.transparentAddressIndex(id, fresh)) > 0)

            verify(session, transparent, fresh) { wallet.transparentAddressIndex(id, it) }
        }
    }

    @Test
    fun sign_v5OrchardAndTransparentPczt_extractsSignedTransaction() = withZcash { assertSignsAndExtracts(it, "v5-orchard") }

    @Test
    fun sign_v6IronwoodPczt_extractsSignedTransaction() = withZcash { assertSignsAndExtracts(it, "v6-ironwood") }

    private suspend fun assertSignsAndExtracts(session: LedgerSession, fixture: String) = withWallet { wallet ->
        val signer = LedgerZcashSigner(signingKey(session))
        val planned = PreparedTransaction(checkNotNull(javaClass.getResource("/zcash/$fixture.pkg")).readBytes())

        val signed = Speculos.approving { signer.sign(wallet, planned) }
        val raw = wallet.extract(signed)

        assertEquals(TXID_LENGTH, ZcashSdk.transactionId(raw).length)
        assertFalse(raw.contentEquals(planned.bytes))
    }

    private suspend fun signingKey(session: LedgerSession): LedgerSigningKey {
        val stored = readKeys(session).first { it.spec.query == unified }.toHardwarePublicKey(AccountFixtures.ACCOUNT_ID)
        val storage = mockk<IHardwarePublicKeyStorage> {
            coEvery { getKey(AccountFixtures.ACCOUNT_ID, BlockchainType.Zcash, unified.tokenType) } returns stored
        }
        val access = LedgerAppAccess(session, LedgerAppKeyFetchers(listOf(fetcher)), storage, LedgerUserOperations())
        return LedgerSigningKey(access, AccountFixtures.account, stored)
    }

    private suspend fun readKeys(session: LedgerSession): List<LedgerKey> = Speculos.approving {
        session.withApp(LedgerAppRequirements.ZCASH_APP, "0") { fetcher.fetchKeys(it, listOf(transparent, unified)) }
    }

    // The wallet's own index of the address stands in for the adapter that locates it.
    private suspend fun verify(
        session: LedgerSession,
        query: TokenQuery,
        address: String,
        index: suspend (String) -> Int? = { null },
    ) {
        val wallet = mockk<Wallet> { every { token.tokenQuery } returns query }
        val locator = mockk<IReceiveAdapter>(moreInterfaces = arrayOf(HardwareAddressLocator::class))
        coEvery { (locator as HardwareAddressLocator).locate(address) } coAnswers {
            index(address)?.let { HardwareAddressPosition(change = false, index = it) }
        }
        val verifier = LedgerZcashAddressVerifier(mockk { every { getReceiveAdapterForWallet(wallet) } returns locator })
        Speculos.approving {
            session.withApp(LedgerAppRequirements.ZCASH_APP, "0") { verifier.verify(it, wallet, address) }
        }
    }

    // Signing needs no server: the wallet points at a closed port.
    private suspend fun <T> withWallet(block: suspend (ZcashWallet) -> T): T {
        ZcashSdk.initialize(sdkDir.absolutePath)
        val database = Files.createTempFile(sdkDir.toPath(), "wallet", ".db").toFile().apply { delete() }
        val wallet = ZcashWallet.open(database.absolutePath, ZcashNetwork.MAIN, ServerConfig(CLOSED_SERVER))
        return try {
            block(wallet)
        } finally {
            wallet.close()
        }
    }

    private fun withZcash(block: suspend (LedgerSession) -> Unit) =
        runBlocking { Speculos.withSession(LedgerAppRequirements.ZCASH_APP, block) }

    private companion object {
        // The SDK takes its data directory once per process.
        val sdkDir: File = Files.createTempDirectory("zcash-ledger").toFile()
        const val ACCOUNT_PATH = "m/44'/133'/0'"
        const val FRESH_COUNT = 3
        const val TXID_LENGTH = 64
        const val CLOSED_SERVER = "http://127.0.0.1:9"
    }
}
