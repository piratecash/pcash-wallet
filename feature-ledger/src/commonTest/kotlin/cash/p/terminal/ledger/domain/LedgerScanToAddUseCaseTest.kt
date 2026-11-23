package cash.p.terminal.ledger.domain

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.ledger.FakeKeyFetcher
import cash.p.terminal.ledger.FakeLedgerDevice
import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.ledgerkit.transport.LedgerTransportProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.hardware.HardwarePublicKeySpecs
import cash.p.terminal.wallet.hardware.HardwareVendor
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LedgerScanToAddUseCaseTest {

    private val ethereum = TokenQuery(BlockchainType.Ethereum, TokenType.Native)
    private val solana = TokenQuery(BlockchainType.Solana, TokenType.Native)
    private val zcash = TokenQuery(BlockchainType.Zcash, TokenType.AddressSpecTyped(TokenType.AddressSpecType.Transparent))
    private val account = mockk<Account> {
        every { id } returns ACCOUNT_ID
        every { type } returns AccountType.LedgerDevice("device", "NanoX", "eth-key")
        every { isHardwareWalletAccount } returns true
    }
    private val accountManager = mockk<IAccountManager> { every { account(ACCOUNT_ID) } returns account }
    private val storage = mockk<IHardwarePublicKeyStorage> { coEvery { save(any()) } just runs }
    private val userOperations = LedgerUserOperations()

    @Test
    fun addTokensByScan_requestedAppHoldsStoredKey_readsProvenAppFirstAndSavesKeys() = runTest {
        val device = FakeLedgerDevice(
            installedApps = mapOf("Ethereum" to "1.12.0", "Solana" to "1.9.0", LedgerAppRequirements.ZCASH_APP to "4.0.0"),
        )
        storeKeys(ethereum to "eth-key", solana to "sol-key")
        val zcashApp = FakeKeyFetcher(LedgerAppRequirements.ZCASH_APP, device, mapOf(zcash to "zec-key"))
        val fetchers = LedgerAppKeyFetchers(
            listOf(
                FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key")),
                FakeKeyFetcher("Solana", device, mapOf(solana to "sol-key")),
                zcashApp,
            ),
        )
        val session = LedgerSession(device.provider)
        val useCase = LedgerScanToAddUseCase(
            accountManager,
            LedgerAppAccess(session, fetchers, storage, userOperations),
            LedgerKeyReader(session, fetchers, userOperations),
            storage,
            TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler), backgroundScope),
            userOperations,
        )

        val allAdded = useCase.addTokensByScan(listOf(zcash, solana), cardId = "", accountId = ACCOUNT_ID)

        assertTrue(allAdded)
        // The same-device re-check runs in the first app read, so Zcash is opened once.
        assertEquals(listOf(LedgerAppRequirements.ZCASH_APP), zcashApp.openAppOnFetch)
        coVerify { storage.save(match { saved -> saved.map { it.key.value } == listOf("sol-key", "zec-key") }) }
    }

    @Test
    fun scan_appNotInstalled_returnsSkippedReasonAndSavesFoundKeys() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        storeKeys(ethereum to "eth-key")
        val useCase = useCase(device, FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key")), solanaFetcher(device))

        val read = useCase.scan(listOf(ethereum, solana), ACCOUNT_ID)

        assertEquals(listOf("eth-key"), read.keys.map { it.key })
        assertEquals("Solana", assertIs<LedgerAppMissingException>(read.skipped.single().reason).appName)
        coVerify { storage.save(match { saved -> saved.map { it.key.value } == listOf("eth-key") }) }
    }

    @Test
    fun addTokensByScan_appNotInstalled_returnsFalse() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        storeKeys(ethereum to "eth-key")
        val useCase = useCase(device, FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key")), solanaFetcher(device))

        assertFalse(useCase.addTokensByScan(listOf(ethereum, solana), cardId = "", accountId = ACCOUNT_ID))
    }

    @Test
    fun addTokensByScan_unsupportedQueryWithoutSkip_returnsFalse() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        storeKeys(ethereum to "eth-key")
        val useCase = useCase(device, FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key")))

        assertFalse(useCase.addTokensByScan(listOf(ethereum, solana), cardId = "", accountId = ACCOUNT_ID))
    }

    @Test
    fun scan_running_userOperationSpansAccountLookupToSave() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0", "Solana" to "1.9.0"))
        storeKeys(ethereum to "eth-key")
        val activity = mutableListOf<Boolean>()
        every { accountManager.account(ACCOUNT_ID) } answers { activity += userOperations.isActive; account }
        coEvery { storage.save(any()) } answers { activity += userOperations.isActive }
        val useCase = useCase(device, FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key")), solanaFetcher(device))

        useCase.scan(listOf(ethereum, solana), ACCOUNT_ID)

        assertEquals(listOf(true, true), activity)
        assertFalse(userOperations.isActive)
    }

    @Test
    fun scan_connectionProvenByEarlierScan_readsWithoutOpeningIdentityApp() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0", "Solana" to "1.9.0"))
        storeKeys(ethereum to "eth-key")
        val ethApp = FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key"))
        val useCase = useCase(device, ethApp, solanaFetcher(device))
        useCase.scan(listOf(solana), ACCOUNT_ID)
        val identityChecks = ethApp.openAppOnFetch.size

        val read = useCase.scan(listOf(solana), ACCOUNT_ID)

        assertEquals(listOf("sol-key"), read.keys.map { it.key })
        assertEquals(identityChecks, ethApp.openAppOnFetch.size)
        coVerify(exactly = 2) { storage.save(match { saved -> saved.map { it.key.value } == listOf("sol-key") }) }
    }

    @Test
    fun scan_connectionChangesDuringRead_provesNewConnectionAndSavesRepeatedRead() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0", "Solana" to "1.9.0"))
        storeKeys(ethereum to "eth-key")
        val session = LedgerSession(device.provider)
        val ethKey = FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key"))
        val ethApp = ConnectionDroppingFetcher(ethKey, session, drops = 1)
        val fetchers = LedgerAppKeyFetchers(listOf(ethApp, solanaFetcher(device)))
        val appAccess = LedgerAppAccess(session, fetchers, storage, userOperations)
        appAccess.withLedgerApp(account, ethereum) {}
        val warmConnection = session.connectionId

        val read = useCase(session, appAccess, fetchers).scan(listOf(ethereum, solana), ACCOUNT_ID)

        assertEquals(listOf("eth-key", "sol-key"), read.keys.map { it.key })
        coVerify(exactly = 1) { storage.save(any()) }
        val proof = appAccess.verifyIdentity(account, emptyList())
        assertNull(proof.app)
        assertNotEquals(warmConnection, proof.connectionId)
    }

    @Test
    fun scan_connectionChangesDuringRepeatedRead_throwsTransportClosedAndSavesNothing() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0", "Solana" to "1.9.0"))
        storeKeys(ethereum to "eth-key")
        val session = LedgerSession(device.provider)
        val ethKey = FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key"))
        val ethApp = ConnectionDroppingFetcher(ethKey, session, drops = 2)
        val fetchers = LedgerAppKeyFetchers(listOf(ethApp, solanaFetcher(device)))
        val appAccess = LedgerAppAccess(session, fetchers, storage, userOperations)
        appAccess.withLedgerApp(account, ethereum) {}

        assertFailsWith<LedgerException.TransportClosed> {
            useCase(session, appAccess, fetchers).scan(listOf(ethereum, solana), ACCOUNT_ID)
        }
        coVerify(exactly = 0) { storage.save(any()) }
    }

    @Test
    fun scan_anotherLedgerAnswersAfterAppSwitch_savesNothingAndDropsProof() = runTest {
        val apps = mapOf("Ethereum" to "1.12.0", "Solana" to "1.9.0")
        val deviceA = FakeLedgerDevice(installedApps = apps)
        val deviceB = FakeLedgerDevice(installedApps = apps, keyPrefix = "B-")
        var opens = 0
        // Device A serves the proof and the first read; the reconnect of the app switch reaches device B.
        val swapped = LedgerTransportProvider { (if (opens++ < 2) deviceA else deviceB).provider.open() }
        val session = LedgerSession(swapped)
        storeKeys(ethereum to "eth-key")
        val fetchers = LedgerAppKeyFetchers(
            listOf(FakeKeyFetcher("Ethereum", deviceA, mapOf(ethereum to "eth-key")), solanaFetcher(deviceB)),
        )
        val appAccess = LedgerAppAccess(session, fetchers, storage, userOperations)
        appAccess.withLedgerApp(account, ethereum) {}

        assertFailsWith<LedgerIdentityMismatchException> {
            useCase(session, appAccess, fetchers).scan(listOf(ethereum, solana), ACCOUNT_ID)
        }
        coVerify(exactly = 0) { storage.save(any()) }
        assertFailsWith<LedgerIdentityMismatchException> { appAccess.verifyIdentity(account, emptyList()) }
    }

    private fun solanaFetcher(device: FakeLedgerDevice) = FakeKeyFetcher("Solana", device, mapOf(solana to "sol-key"))

    private fun TestScope.useCase(device: FakeLedgerDevice, vararg fetchers: LedgerAppKeyFetcher): LedgerScanToAddUseCase {
        val appFetchers = LedgerAppKeyFetchers(fetchers.toList())
        val session = LedgerSession(device.provider)
        return useCase(session, LedgerAppAccess(session, appFetchers, storage, userOperations), appFetchers)
    }

    private fun TestScope.useCase(
        session: LedgerSession,
        appAccess: LedgerAppAccess,
        fetchers: LedgerAppKeyFetchers,
    ) = LedgerScanToAddUseCase(
        accountManager,
        appAccess,
        LedgerKeyReader(session, fetchers, userOperations),
        storage,
        TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler), backgroundScope),
        userOperations,
    )

    /** Drops the session's connection after each of its first [drops] key reads, as an unnoticed replug would. */
    private class ConnectionDroppingFetcher(
        private val inner: LedgerAppKeyFetcher,
        private val session: LedgerSession,
        private var drops: Int,
    ) : LedgerAppKeyFetcher by inner {
        override suspend fun fetchKeys(transport: LedgerTransport, queries: List<TokenQuery>): List<LedgerKey> =
            inner.fetchKeys(transport, queries).also { if (drops-- > 0) session.close() }
    }

    private fun storeKeys(vararg keys: Pair<TokenQuery, String>) {
        val stored = keys.map { (query, key) -> query to storedKey(query, key) }
        coEvery { storage.getAllPublicKeys(ACCOUNT_ID) } returns stored.map { it.second }
        stored.forEach { (query, key) ->
            coEvery { storage.getKey(ACCOUNT_ID, query.blockchainType, query.tokenType) } returns key
        }
    }

    private fun storedKey(query: TokenQuery, key: String): HardwarePublicKey {
        val spec = checkNotNull(HardwarePublicKeySpecs.specFor(query, HardwareVendor.Ledger))
        return LedgerKey(spec, key, byteArrayOf(1)).toHardwarePublicKey(ACCOUNT_ID)
    }

    private companion object {
        const val ACCOUNT_ID = "ledger-account"
    }
}
