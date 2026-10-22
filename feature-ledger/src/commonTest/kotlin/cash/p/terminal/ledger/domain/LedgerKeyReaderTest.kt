package cash.p.terminal.ledger.domain

import cash.p.terminal.ledger.FakeKeyFetcher
import cash.p.terminal.ledger.FakeLedgerDevice
import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.transport.LedgerTransportProvider
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

class LedgerKeyReaderTest {

    private val ethereum = TokenQuery(BlockchainType.Ethereum, TokenType.Native)
    private val bsc = TokenQuery(BlockchainType.BinanceSmartChain, TokenType.Native)
    private val solana = TokenQuery(BlockchainType.Solana, TokenType.Native)
    private val unsupported = TokenQuery(BlockchainType.Tron, TokenType.Native)
    private val userOperations = LedgerUserOperations()

    @Test
    fun read_allAppsInstalled_readsEachKeyInsideItsAppAndReportsProgress() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0", "Solana" to "1.9.0"))
        val ethApp = FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key", bsc to "bsc-key"))
        val solApp = FakeKeyFetcher("Solana", device, mapOf(solana to "sol-key"))
        val progress = mutableListOf<LedgerAppProgress>()

        val read = reader(device, ethApp, solApp)
            .read(listOf(ethereum, solana, bsc, unsupported), null) { progress += it }

        assertEquals(listOf("eth-key", "bsc-key", "sol-key"), read.keys.map { it.key })
        assertEquals(emptyList(), read.skippedApps)
        // The third Ethereum read re-checks the first key after switching apps.
        assertEquals(listOf("Ethereum", "Ethereum", "Ethereum"), ethApp.openAppOnFetch)
        assertEquals(listOf("Solana"), solApp.openAppOnFetch)
        assertEquals(
            listOf(
                LedgerAppProgress(1, 3, "Ethereum"),
                LedgerAppProgress(2, 3, "Solana"),
                LedgerAppProgress(3, 3, "Ethereum"),
            ),
            progress,
        )
    }

    @Test
    fun read_appNotInstalled_skipsAppAndKeepsOthers() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        val ethApp = FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key"))
        val solApp = FakeKeyFetcher("Solana", device, mapOf(solana to "sol-key"))

        val read = reader(device, ethApp, solApp).read(listOf(solana, ethereum), null) {}

        assertEquals(listOf("eth-key"), read.keys.map { it.key })
        assertEquals(listOf("Solana"), read.skippedApps)
        assertEquals("Solana", assertIs<LedgerAppMissingException>(read.skipped.single().reason).appName)
    }

    @Test
    fun read_userRejectsOpeningApp_skipsApp() = runTest {
        val device = FakeLedgerDevice(
            installedApps = mapOf("Ethereum" to "1.12.0", "Solana" to "1.9.0"),
            rejectedApps = setOf("Ethereum"),
        )
        val ethApp = FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key"))
        val solApp = FakeKeyFetcher("Solana", device, mapOf(solana to "sol-key"))

        val read = reader(device, ethApp, solApp).read(listOf(ethereum, solana), null) {}

        assertEquals(listOf("sol-key"), read.keys.map { it.key })
        assertEquals(listOf("Ethereum"), read.skippedApps)
        assertIs<LedgerRejectedException>(read.skipped.single().reason)
        assertEquals(emptyList(), ethApp.openAppOnFetch)
    }

    @Test
    fun read_appOlderThanRequired_skipsApp() = runTest {
        val zcash = TokenQuery(BlockchainType.Zcash, TokenType.AddressSpecTyped(TokenType.AddressSpecType.Transparent))
        val device = FakeLedgerDevice(installedApps = mapOf(LedgerAppRequirements.ZCASH_APP to "3.8.9"))
        val zcashApp = FakeKeyFetcher(LedgerAppRequirements.ZCASH_APP, device, mapOf(zcash to "zec-key"))

        val read = reader(device, zcashApp).read(listOf(zcash), null) {}

        assertEquals(emptyList(), read.keys)
        assertEquals(listOf(LedgerAppRequirements.ZCASH_APP), read.skippedApps)
        val reason = assertIs<LedgerException.AppVersionTooOld>(read.skipped.single().reason)
        assertEquals(LedgerAppRequirements.ZCASH_APP, reason.appName)
    }

    @Test
    fun read_deviceOpensAnotherApp_failsWithoutReadingKeys() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"), openedInstead = "Bitcoin")
        val ethApp = FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key"))

        assertFailsWith<LedgerException.WrongAppOpen> {
            reader(device, ethApp).read(listOf(ethereum), null) {}
        }
        assertEquals(emptyList(), ethApp.openAppOnFetch)
    }

    @Test
    fun read_anotherLedgerAnswersAfterAppSwitch_throwsIdentityMismatch() = runTest {
        val apps = mapOf("Ethereum" to "1.12.0", "Solana" to "1.9.0")
        val deviceA = FakeLedgerDevice(installedApps = apps, keyPrefix = "A-")
        val deviceB = FakeLedgerDevice(installedApps = apps, keyPrefix = "B-")
        var opens = 0
        // Device A serves the dashboard and the first app; every reconnection after that reaches device B.
        val swapped = LedgerTransportProvider { (if (opens++ < 2) deviceA else deviceB).provider.open() }
        val ethApp = FakeKeyFetcher("Ethereum", deviceA, mapOf(ethereum to "eth-key"))
        val solApp = FakeKeyFetcher("Solana", deviceB, mapOf(solana to "sol-key"))
        val reader = reader(swapped, ethApp, solApp)

        assertFailsWith<LedgerIdentityMismatchException> { reader.read(listOf(ethereum, solana), null) {} }
    }

    @Test
    fun read_userRejectsFinalReopen_throwsLedgerRejected() = runTest {
        val device = FakeLedgerDevice(
            installedApps = mapOf("Ethereum" to "1.12.0", "Solana" to "1.9.0"),
            rejectedReopens = setOf("Ethereum"),
        )
        val ethApp = FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key"))
        val solApp = FakeKeyFetcher("Solana", device, mapOf(solana to "sol-key"))

        assertFailsWith<LedgerRejectedException> {
            reader(device, ethApp, solApp).read(listOf(ethereum, solana), null) {}
        }
    }

    @Test
    fun read_severalApps_everyExchangeUnderUserOperation() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0", "Solana" to "1.9.0"))
        val ethApp = FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key"))
        val solApp = FakeKeyFetcher("Solana", device, mapOf(solana to "sol-key"))
        val activity = mutableListOf<Boolean>()

        reader(device.provider.recordingActivity(userOperations, activity), ethApp, solApp)
            .read(listOf(ethereum, solana), null) {}

        assertEquals(listOf(true), activity.distinct())
        assertFalse(userOperations.isActive)
    }

    @Test
    fun read_expectedConnectionUnchanged_readsKeysAcrossAppSwitches() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0", "Solana" to "1.9.0"))
        val session = provenSession(device)
        val ethApp = FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key"))
        val solApp = FakeKeyFetcher("Solana", device, mapOf(solana to "sol-key"))

        val read = reader(session, ethApp, solApp).read(listOf(ethereum, solana), session.connectionId) {}

        assertEquals(listOf("eth-key", "sol-key"), read.keys.map { it.key })
    }

    @Test
    fun read_connectionChangedSinceProof_throwsConnectionChangedWithoutReadingKeys() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        val session = provenSession(device)
        val proven = session.connectionId
        val ethApp = FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key"))
        session.close()

        assertFailsWith<LedgerConnectionChangedException> {
            reader(session, ethApp).read(listOf(ethereum), proven) {}
        }
        assertEquals(emptyList(), ethApp.openAppOnFetch)
    }

    @Test
    fun read_expectedConnectionAppNotInstalled_skipsApp() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        val session = provenSession(device)
        val ethApp = FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key"))
        val solApp = FakeKeyFetcher("Solana", device, mapOf(solana to "sol-key"))

        val read = reader(session, ethApp, solApp).read(listOf(ethereum, solana), session.connectionId) {}

        assertEquals(listOf("eth-key"), read.keys.map { it.key })
        assertEquals(listOf("Solana"), read.skippedApps)
    }

    @Test
    fun read_noExpectedConnectionAfterReconnect_readsKeys() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        val session = provenSession(device)
        session.close()

        val read = reader(session, FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key")))
            .read(listOf(ethereum), null) {}

        assertEquals(listOf("eth-key"), read.keys.map { it.key })
    }

    private suspend fun provenSession(device: FakeLedgerDevice) =
        LedgerSession(device.provider).also { it.withApp("Ethereum", "1.12.0") {} }

    private fun reader(device: FakeLedgerDevice, vararg fetchers: LedgerAppKeyFetcher) =
        reader(device.provider, *fetchers)

    private fun reader(provider: LedgerTransportProvider, vararg fetchers: LedgerAppKeyFetcher) =
        reader(LedgerSession(provider), *fetchers)

    private fun reader(session: LedgerSession, vararg fetchers: LedgerAppKeyFetcher) =
        LedgerKeyReader(session, LedgerAppKeyFetchers(fetchers.toList()), userOperations)
}
