package cash.p.terminal.ledger.domain

import cash.p.terminal.ledger.FakeKeyFetcher
import cash.p.terminal.ledger.FakeLedgerDevice
import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.hardware.HardwareCancelledException
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LedgerAppAccessTest {

    private val ethereum = TokenQuery(BlockchainType.Ethereum, TokenType.Native)
    private val zcash = TokenQuery(BlockchainType.Zcash, TokenType.AddressSpecTyped(TokenType.AddressSpecType.Transparent))
    private val bothApps = mapOf("Ethereum" to "1.12.0", LedgerAppRequirements.ZCASH_APP to "4.0.0")
    private val storage = mockk<IHardwarePublicKeyStorage>()
    private val userOperations = LedgerUserOperations()
    private val account = mockk<Account> {
        every { id } returns ACCOUNT_ID
        every { type } returns AccountType.LedgerDevice("device", "NanoX", "eth-key")
        every { isHardwareWalletAccount } returns true
    }

    @Test
    fun withLedgerApp_storedKeyMatches_runsBlockInsideApp() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        storeKey(ethereum, "eth-key")

        val app = access(device, FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key")))
            .withLedgerApp(account, ethereum) { device.currentApp }

        assertEquals("Ethereum", app)
    }

    @Test
    fun withLedgerApp_tokenOnChainKey_asksDeviceForChainKey() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        val usdt = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xdac17f958d2ee523a2206206994597c13d831ec7"))
        val chainKey = storeKey(ethereum, "eth-key")
        coEvery { storage.getKey(ACCOUNT_ID, usdt.blockchainType, usdt.tokenType) } returns null
        coEvery { storage.getKeyByBlockchain(ACCOUNT_ID, BlockchainType.Ethereum) } returns chainKey

        val app = access(device, FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key")))
            .withLedgerApp(account, usdt) { device.currentApp }

        assertEquals("Ethereum", app)
    }

    @Test
    fun withLedgerApp_blockRunning_userOperationActiveOnlyInsideBlock() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        storeKey(ethereum, "eth-key")

        val activeInside = access(device, FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key")))
            .withLedgerApp(account, ethereum) { userOperations.isActive }

        assertTrue(activeInside)
        assertFalse(userOperations.isActive)
    }

    @Test
    fun withLedgerApp_admitOverload_notUserOperation() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        storeKey(ethereum, "eth-key")

        val activeInside = access(device, FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key")))
            .withLedgerApp(account, ethereum, admit = {}, switchApp = true) { userOperations.isActive }

        assertFalse(activeInside)
    }

    @Test
    fun isOpen_appSwitchedSinceLastOperation_reportsAppOnDevice() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0", "Monero" to "2.0.0"))
        storeKey(ethereum, "eth-key")
        val access = access(device, FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key")))
        access.withLedgerApp(account, ethereum) {}

        LedgerSession(device.provider).withApp("Monero", "2.0.0") {}

        assertTrue(access.isOpen("Monero", admit = {}))
        assertFalse(access.isOpen("Ethereum", admit = {}))
    }

    @Test
    fun isOpen_noOperationYet_falseWithoutConnecting() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        val access = access(device, FakeKeyFetcher("Ethereum", device, emptyMap()))

        assertFalse(access.isOpen("Ethereum", admit = {}))
        assertEquals(0, device.opens)
    }

    @Test
    fun isOpen_deviceUnpluggedAfterOperation_false() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        storeKey(ethereum, "eth-key")
        val access = access(device, FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key")))
        access.withLedgerApp(account, ethereum) {}

        device.unplugged = true

        assertFalse(access.isOpen("Ethereum", admit = {}))
    }

    @Test
    fun withLedgerApp_otherKeyOnDevice_throwsIdentityMismatchWithoutRunningBlock() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        storeKey(ethereum, "eth-key")
        var blockRan = false

        assertFailsWith<LedgerIdentityMismatchException> {
            access(device, FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "other-passphrase-key")))
                .withLedgerApp(account, ethereum) { blockRan = true }
        }
        assertFalse(blockRan)
    }

    @Test
    fun withLedgerApp_appOlderThanRequired_throwsAppVersionTooOldWithoutRunningBlock() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf(LedgerAppRequirements.ZCASH_APP to "3.8.0"))
        storeKey(zcash, "zec-key")
        var blockRan = false

        val error = assertFailsWith<LedgerException.AppVersionTooOld> {
            access(device, FakeKeyFetcher(LedgerAppRequirements.ZCASH_APP, device, mapOf(zcash to "zec-key")))
                .withLedgerApp(account, zcash) { blockRan = true }
        }
        assertEquals(LedgerAppRequirements.ZCASH_APP, error.appName)
        assertFalse(blockRan)
    }

    @Test
    fun withLedgerApp_appNotInstalled_throwsAppMissingWithAppName() = runTest {
        val device = FakeLedgerDevice(installedApps = emptyMap())
        storeKey(ethereum, "eth-key")

        val error = assertFailsWith<LedgerAppMissingException> {
            access(device, FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key")))
                .withLedgerApp(account, ethereum) {}
        }
        assertEquals("Ethereum", error.appName)
    }

    @Test
    fun withLedgerApp_switchForbiddenOtherAppOpen_throwsWrongAppOpenWithoutSwitching() = runTest {
        val device = FakeLedgerDevice(installedApps = bothApps)
        storeKey(zcash, "zec-key")
        val session = LedgerSession(device.provider)
        session.withApp("Ethereum", "1.12.0") {}
        var blockRan = false

        assertFailsWith<LedgerException.WrongAppOpen> {
            access(device, zcashApp(device, "zec-key"), session = session)
                .withLedgerApp(account, zcash, admit = {}, switchApp = false) { blockRan = true }
        }
        assertEquals("Ethereum", device.currentApp)
        assertFalse(blockRan)
    }

    @Test
    fun withLedgerApp_switchForbiddenAppOpen_runsBlock() = runTest {
        val device = FakeLedgerDevice(installedApps = bothApps)
        storeKey(zcash, "zec-key")
        val session = LedgerSession(device.provider)
        session.withApp(LedgerAppRequirements.ZCASH_APP, "4.0.0") {}

        val app = access(device, zcashApp(device, "zec-key"), session = session)
            .withLedgerApp(account, zcash, admit = {}, switchApp = false) { device.currentApp }

        assertEquals(LedgerAppRequirements.ZCASH_APP, app)
    }

    @Test
    fun verifyIdentity_otherKeyOnDevice_throwsIdentityMismatch() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        val stored = storeKey(ethereum, "eth-key")
        coEvery { storage.getAllPublicKeys(ACCOUNT_ID) } returns listOf(stored)

        assertFailsWith<LedgerIdentityMismatchException> {
            access(device, FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "another-ledger-key")))
                .verifyIdentity(account, emptyList())
        }
    }

    @Test
    fun verifyIdentity_identityAppRemovedOtherAppHoldsStoredKey_succeedsInOtherApp() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf(LedgerAppRequirements.ZCASH_APP to "4.0.0"))
        storeAllKeys()

        val provenApp = identityAccess(device, zcashKeyOnDevice = "zec-key").verifyIdentity(account, emptyList()).app

        assertEquals(LedgerAppRequirements.ZCASH_APP, provenApp)
        assertEquals(LedgerAppRequirements.ZCASH_APP, device.currentApp)
    }

    @Test
    fun verifyIdentity_requestedTokenOnStoredKeyApp_provesThereWithoutOpeningIdentityApp() = runTest {
        val device = FakeLedgerDevice(installedApps = bothApps)
        storeAllKeys()
        val ethApp = FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key"))

        val provenApp = access(device, ethApp, zcashApp(device, "zec-key")).verifyIdentity(account, listOf(zcash)).app

        assertEquals(LedgerAppRequirements.ZCASH_APP, provenApp)
        assertEquals(emptyList(), ethApp.openAppOnFetch)
    }

    @Test
    fun verifyIdentity_storedKeyAppOpenOnHeldConnection_provesThereWithoutOpeningAnotherApp() = runTest {
        val device = FakeLedgerDevice(installedApps = bothApps)
        storeAllKeys()
        val session = LedgerSession(device.provider)
        session.withApp(LedgerAppRequirements.ZCASH_APP, "4.0.0") {}
        val opensBefore = device.opens
        val ethApp = FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key"))

        val provenApp = access(device, ethApp, zcashApp(device, "zec-key"), session = session)
            .verifyIdentity(account, emptyList()).app

        assertEquals(LedgerAppRequirements.ZCASH_APP, provenApp)
        assertEquals(opensBefore, device.opens)
        assertEquals(emptyList(), ethApp.openAppOnFetch)
    }

    @Test
    fun verifyIdentity_heldConnection_everyExchangeUnderUserOperation() = runTest {
        val device = FakeLedgerDevice(installedApps = bothApps)
        storeAllKeys()
        val activity = mutableListOf<Boolean>()
        val session = LedgerSession(device.provider.recordingActivity(userOperations, activity))
        session.withApp(LedgerAppRequirements.ZCASH_APP, "4.0.0") {}
        activity.clear()
        val ethApp = FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key"))

        access(device, ethApp, zcashApp(device, "zec-key"), session = session).verifyIdentity(account, emptyList())

        assertEquals(listOf(true), activity.distinct())
        assertFalse(userOperations.isActive)
    }

    @Test
    fun verifyIdentity_openAppOutdatedOtherStoredKeyAppCurrent_provesInOtherApp() = runTest {
        val device = FakeLedgerDevice(
            installedApps = mapOf("Ethereum" to "1.12.0", LedgerAppRequirements.ZCASH_APP to "3.8.0"),
        )
        storeAllKeys()
        val session = LedgerSession(device.provider)
        session.withApp(LedgerAppRequirements.ZCASH_APP, "3.8.0") {}

        val provenApp = access(
            device,
            FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key")),
            zcashApp(device, "zec-key"),
            session = session,
        ).verifyIdentity(account, emptyList()).app

        assertEquals("Ethereum", provenApp)
    }

    @Test
    fun verifyIdentity_noCandidateUsableOneOutdated_throwsAppVersionTooOld() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf(LedgerAppRequirements.ZCASH_APP to "3.8.0"))
        storeAllKeys()

        val error = assertFailsWith<LedgerException.AppVersionTooOld> {
            identityAccess(device, zcashKeyOnDevice = "zec-key").verifyIdentity(account, emptyList())
        }
        assertEquals(LedgerAppRequirements.ZCASH_APP, error.appName)
    }

    @Test
    fun verifyIdentity_preferredAppReturnsAnotherKey_throwsIdentityMismatch() = runTest {
        val device = FakeLedgerDevice(installedApps = bothApps)
        storeAllKeys()

        assertFailsWith<LedgerIdentityMismatchException> {
            identityAccess(device, zcashKeyOnDevice = "another-ledger-key").verifyIdentity(account, listOf(zcash))
        }
    }

    @Test
    fun verifyIdentity_identityAppRemovedOtherAppReturnsAnotherKey_throwsIdentityMismatch() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf(LedgerAppRequirements.ZCASH_APP to "4.0.0"))
        storeAllKeys()

        assertFailsWith<LedgerIdentityMismatchException> {
            identityAccess(device, zcashKeyOnDevice = "another-ledger-key").verifyIdentity(account, emptyList())
        }
    }

    @Test
    fun verifyIdentity_noStoredKeyAppInstalled_throwsIdentityAppMissingIdentityAppFirst() = runTest {
        val device = FakeLedgerDevice(installedApps = emptyMap())
        storeAllKeys()

        val error = assertFailsWith<LedgerIdentityAppMissingException> {
            identityAccess(device, zcashKeyOnDevice = "zec-key").verifyIdentity(account, emptyList())
        }
        assertEquals(listOf("Ethereum", LedgerAppRequirements.ZCASH_APP), error.appNames)
    }

    @Test
    fun verifyIdentity_connectionProvenBefore_returnsProofWithoutDeviceExchange() = runTest {
        val device = FakeLedgerDevice(installedApps = bothApps)
        storeAllKeys()
        val exchanges = mutableListOf<Boolean>()
        val session = LedgerSession(device.provider.recordingActivity(userOperations, exchanges))
        val access = identityAccess(device, zcashKeyOnDevice = "zec-key", session)
        val first = access.verifyIdentity(account, emptyList())
        exchanges.clear()

        val second = access.verifyIdentity(account, emptyList())

        assertNull(second.app)
        assertEquals(first.connectionId, second.connectionId)
        assertEquals(emptyList(), exchanges)
    }

    @Test
    fun verifyIdentity_connectionChangedSinceProof_provesAgain() = runTest {
        val device = FakeLedgerDevice(installedApps = bothApps)
        storeAllKeys()
        val session = LedgerSession(device.provider)
        val access = identityAccess(device, zcashKeyOnDevice = "zec-key", session)
        val first = access.verifyIdentity(account, emptyList())
        session.close()
        session.withApp("Ethereum", "1.12.0") {}

        val second = access.verifyIdentity(account, emptyList())

        assertEquals("Ethereum", second.app)
        assertNotEquals(first.connectionId, second.connectionId)
    }

    @Test
    fun withLedgerApp_storedKeyMatches_provesConnectionForVerifyIdentity() = runTest {
        val device = FakeLedgerDevice(installedApps = bothApps)
        storeAllKeys()
        val exchanges = mutableListOf<Boolean>()
        val session = LedgerSession(device.provider.recordingActivity(userOperations, exchanges))
        val access = identityAccess(device, zcashKeyOnDevice = "zec-key", session)
        access.withLedgerApp(account, zcash) {}
        exchanges.clear()

        val proof = access.verifyIdentity(account, emptyList())

        assertNull(proof.app)
        assertEquals(emptyList(), exchanges)
    }

    @Test
    fun withLedgerApp_otherKeyOnProvenConnection_dropsProof() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        storeAllKeys()
        val keysOnDevice = mutableMapOf(ethereum to "eth-key")
        val access = access(device, FakeKeyFetcher("Ethereum", device, keysOnDevice))
        access.withLedgerApp(account, ethereum) {}
        keysOnDevice[ethereum] = "other-passphrase-key"
        assertFailsWith<LedgerIdentityMismatchException> { access.withLedgerApp(account, ethereum) {} }
        keysOnDevice[ethereum] = "eth-key"

        val proof = access.verifyIdentity(account, emptyList())

        assertEquals("Ethereum", proof.app)
    }

    @Test
    fun signWithLedgerApp_garbledAnswer_throwsSigningFailed() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        storeKey(ethereum, "eth-key")

        assertFailsWith<LedgerSigningFailedException> {
            access(device, FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key")))
                .signWithLedgerApp(account, ethereum) { throw LedgerException.MalformedResponse("signature") }
        }
    }

    @Test
    fun signWithLedgerApp_blindSigningOff_throwsInstructionWithAppName() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        storeKey(ethereum, "eth-key")

        val error = assertFailsWith<LedgerBlindSigningRequiredException> {
            access(device, FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key")))
                .signWithLedgerApp(account, ethereum) { throw LedgerException.BlindSigningRequired() }
        }
        assertEquals("Ethereum", error.appName)
    }

    @Test
    fun signingKeySign_storedKeyOnDevice_signsAtStoredPath() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        val stored = storeKey(ethereum, "eth-key")

        val path = signingKey(device, deviceKey = "eth-key", stored).sign { _, path -> path.toString() }

        assertEquals("44'/60'/0'/0/0", path)
    }

    @Test
    fun signingKeySign_otherKeyOnDevice_throwsIdentityMismatchWithoutSigning() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        val stored = storeKey(ethereum, "eth-key")
        var signed = false

        assertFailsWith<LedgerIdentityMismatchException> {
            signingKey(device, deviceKey = "other-key", stored).sign { _, _ -> signed = true }
        }
        assertFalse(signed)
    }

    @Test
    fun signingKeySign_rejectedOnDevice_throwsCancellation() = runTest {
        val device = FakeLedgerDevice(installedApps = mapOf("Ethereum" to "1.12.0"))
        val stored = storeKey(ethereum, "eth-key")

        assertFailsWith<HardwareCancelledException> {
            signingKey(device, deviceKey = "eth-key", stored).sign { _, _ -> throw LedgerException.UserRejected() }
        }
    }

    private fun storeKey(query: TokenQuery, key: String): HardwarePublicKey {
        val stored = HardwarePublicKey(
            accountId = ACCOUNT_ID,
            blockchainType = query.blockchainType.uid,
            type = HardwarePublicKeyType.PUBLIC_KEY,
            tokenType = query.tokenType,
            key = SecretString(key),
            derivationPath = "m/44'/60'/0'/0/0",
            publicKey = byteArrayOf(1),
            derivedPublicKey = byteArrayOf(1),
        )
        coEvery { storage.getKey(ACCOUNT_ID, query.blockchainType, query.tokenType) } returns stored
        return stored
    }

    private fun signingKey(device: FakeLedgerDevice, deviceKey: String, stored: HardwarePublicKey): LedgerSigningKey {
        val fetcher = FakeKeyFetcher("Ethereum", device, mapOf(ethereum to deviceKey))
        return LedgerSigningKey(access(device, fetcher), account, stored)
    }

    /** The identity (Ethereum) key is stored after the Zcash one, so the order proves it is tried first. */
    private fun storeAllKeys() {
        val stored = listOf(storeKey(zcash, "zec-key"), storeKey(ethereum, "eth-key"))
        coEvery { storage.getAllPublicKeys(ACCOUNT_ID) } returns stored
    }

    private fun identityAccess(
        device: FakeLedgerDevice,
        zcashKeyOnDevice: String,
        session: LedgerSession = LedgerSession(device.provider),
    ) = access(
        device,
        FakeKeyFetcher("Ethereum", device, mapOf(ethereum to "eth-key")),
        zcashApp(device, zcashKeyOnDevice),
        session = session,
    )

    private fun zcashApp(device: FakeLedgerDevice, keyOnDevice: String) =
        FakeKeyFetcher(LedgerAppRequirements.ZCASH_APP, device, mapOf(zcash to keyOnDevice))

    private fun access(
        device: FakeLedgerDevice,
        vararg fetchers: LedgerAppKeyFetcher,
        session: LedgerSession = LedgerSession(device.provider),
    ) = LedgerAppAccess(session, LedgerAppKeyFetchers(fetchers.toList()), storage, userOperations)

    private companion object {
        const val ACCOUNT_ID = "ledger-account"
    }
}
