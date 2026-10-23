package cash.p.terminal.core.managers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.ledger.di.ledgerAppKeyFetchers
import cash.p.terminal.ledger.domain.LedgerAppAccess
import cash.p.terminal.ledger.domain.LedgerIdentityMismatchException
import cash.p.terminal.ledger.domain.LedgerRejectedException
import cash.p.terminal.ledger.domain.LedgerUserOperations
import cash.p.terminal.ledger.domain.LedgerWaitNotice
import cash.p.terminal.ledger.domain.LedgerWaitReason
import cash.p.terminal.ledger.monero.LEDGER_MONERO_APP
import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.entities.TokenType
import com.piratecash.monero.signer.ExternalSigner
import com.piratecash.monero.signer.ExternalSignerApduChannel
import com.piratecash.monero.signer.ExternalSignerException
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.Call
import io.mockk.MockKAnswerScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationTargetException
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class MoneroLedgerOperationGatewayTest {

    private val session = mockk<LedgerSession> { every { connectionId } returns 1L }
    private var appInForeground = true
    private var userOperationActive = false
    private var apdusSent = 0
    private var transportLost: Exception? = null
    private val userOperations = mockk<LedgerUserOperations> { every { isActive } answers { userOperationActive } }
    private val storage = mockk<IHardwarePublicKeyStorage>()
    private val dispatcher = UnconfinedTestDispatcher()
    private val gateway = gateway(userOperations)

    @Test
    fun execute_otherWalletOnDevice_neverRunsBlock() = runTest {
        connect(deviceAddress = ADDRESS.reversed())
        var blockRan = false

        assertFailsWith<LedgerIdentityMismatchException> {
            gateway.execute(ACCOUNT) { blockRan = true }
        }
        assertFalse(blockRan)
    }

    @Test
    fun execute_runningBlock_keepsLedgerChannelRegisteredUntilDone() = runTest {
        connect(deviceAddress = ADDRESS)
        var secondChannelRefused = false

        val walletKey = gateway.execute(ACCOUNT) { walletKey ->
            secondChannelRefused = runCatching { ExternalSigner.registerApdu(IdleChannel) }
                .exceptionOrNull() is ExternalSignerException
            walletKey
        }

        assertEquals(WALLET_KEY, walletKey)
        assertTrue(secondChannelRefused)
        ExternalSigner.registerApdu(IdleChannel).release()
    }

    @Test
    fun executeUnattended_appInBackground_defersWithoutRunningBlock() = runTest {
        connect(deviceAddress = ADDRESS)
        appInForeground = false
        var blockRan = false

        assertFailsWith<LedgerSessionDeferredException> {
            gateway.executeUnattended(ACCOUNT) { blockRan = true }
        }
        assertFalse(blockRan)
    }

    @Test
    fun executeUnattended_switchForbiddenOtherAppOpen_defersWithoutSwitching() = runTest {
        connect(deviceAddress = ADDRESS)
        coEvery { session.withOpenApp<Any?>(LEDGER_MONERO_APP, any(), any(), any()) } throws
            LedgerException.WrongAppOpen()
        var blockRan = false

        assertFailsWith<LedgerSessionDeferredException> {
            withContext(LedgerAppSwitchForbidden) { gateway.executeUnattended(ACCOUNT) { blockRan = true } }
        }
        assertFalse(blockRan)
        coVerify(exactly = 0) { session.withApp<Any?>(any(), any(), any(), any()) }
    }

    @Test
    fun executeUnattended_switchForbiddenMoneroOpen_runsBlockWithoutSwitching() = runTest {
        connect(deviceAddress = ADDRESS)

        val walletKey = withContext(LedgerAppSwitchForbidden) { gateway.executeUnattended(ACCOUNT) { it } }

        assertEquals(WALLET_KEY, walletKey)
        coVerify(exactly = 0) { session.withApp<Any?>(any(), any(), any(), any()) }
    }

    @Test
    fun executeUnattended_switchAllowed_opensMoneroAppIfNeeded() = runTest {
        connect(deviceAddress = ADDRESS)

        assertEquals(WALLET_KEY, gateway.executeUnattended(ACCOUNT) { it })
        coVerify(exactly = 0) { session.withOpenApp<Any?>(any(), any(), any(), any()) }
    }

    @Test
    fun execute_appInBackground_runsUserOperation() = runTest {
        connect(deviceAddress = ADDRESS)
        appInForeground = false

        assertEquals(WALLET_KEY, gateway.execute(ACCOUNT) { it })
    }

    @Test
    fun executeYielding_userOperationActive_defersWithoutRunningBlock() = runTest {
        connect(deviceAddress = ADDRESS)
        userOperationActive = true
        var blockRan = false

        assertFailsWith<LedgerSessionDeferredException> {
            gateway.executeYielding(ACCOUNT) { blockRan = true }
        }
        assertFalse(blockRan)
        assertEquals(0, apdusSent)
    }

    @Test
    fun executeYielding_userOperationStartsMidBlock_stillExchangesApdus() = runTest {
        connect(deviceAddress = ADDRESS)

        val apdus = gateway.executeYielding(ACCOUNT) {
            walletExchange()
            userOperationActive = true
            apdusReachingDevice(::walletExchange)
        }

        assertEquals(1, apdus)
    }

    @Test
    fun executeYielding_blockYieldsAtSafePoint_throwsYielded() = runTest {
        connect(deviceAddress = ADDRESS)

        assertFailsWith<LedgerSessionYieldedException> {
            gateway.executeYielding(ACCOUNT) {
                walletExchange()
                throw LedgerSessionYieldedException()
            }
        }
    }

    @Test
    fun executeYielding_callerCancelledMidBlock_blockStillReachesDeviceUninterrupted() = runTest {
        connect(deviceAddress = ADDRESS)
        var apdus = 0
        var interrupted = true

        launch {
            val caller = coroutineContext.job
            gateway.executeYielding(ACCOUNT) {
                caller.cancel()
                apdus = apdusReachingDevice(::walletExchange)
                interrupted = Thread.interrupted()
            }
        }.join()

        assertEquals(1, apdus)
        assertFalse(interrupted)
    }

    @Test
    fun executeYielding_deviceFailureSwallowedByBlock_throwsTransportFailure() = runTest {
        connect(deviceAddress = ADDRESS)
        var intactAfterFailure = true

        assertFailsWith<LedgerException.TransportClosed> {
            gateway.executeYielding(ACCOUNT) {
                transportLost = LedgerException.TransportClosed()
                // wallet2 swallows a failed exchange and goes on.
                runCatching { walletExchange() }
                intactAfterFailure = gateway.isSessionIntact
            }
        }
        assertFalse(intactAfterFailure)
    }

    @Test
    fun executeUnattended_userOperationActive_stillExchangesApdus() = runTest {
        connect(deviceAddress = ADDRESS)
        userOperationActive = true

        assertEquals(1, gateway.executeUnattended(ACCOUNT) { apdusReachingDevice(::walletExchange) })
    }

    @Test
    fun execute_userOperationActive_stillExchangesApdus() = runTest {
        connect(deviceAddress = ADDRESS)
        userOperationActive = true

        assertEquals(1, gateway.execute(ACCOUNT) { apdusReachingDevice(::walletExchange) })
    }

    @Test
    fun executeYielding_userOperationWaitsPastFirstNotice_remindsOfMoneroSync() = runTest {
        connect(deviceAddress = ADDRESS)
        val operations = LedgerUserOperations()
        val notices = waitNotices(operations)

        gateway(operations).executeYielding(ACCOUNT) { userOperationWaits(operations, millis = 2_000) }

        assertEquals(listOf(LedgerWaitNotice(LEDGER_MONERO_APP, LedgerWaitReason.Sync)), notices)
    }

    @Test
    fun executeYielding_sessionEndsWhileUserOperationWaits_stopsReminding() = runTest {
        connect(deviceAddress = ADDRESS)
        val operations = LedgerUserOperations()
        val notices = waitNotices(operations)

        gateway(operations).executeYielding(ACCOUNT) { userOperationWaits(operations, millis = 1_000) }
        testScheduler.advanceTimeBy(30_000)

        assertEquals(emptyList<LedgerWaitNotice>(), notices)
    }

    @Test
    fun executeUnattended_userOperationWaitsPastFirstNotice_remindsOfMoneroDeviceRequest() = runTest {
        connect(deviceAddress = ADDRESS)
        val operations = LedgerUserOperations()
        val notices = waitNotices(operations)

        gateway(operations).executeUnattended(ACCOUNT) { userOperationWaits(operations, millis = 2_000) }

        assertEquals(listOf(LedgerWaitNotice(LEDGER_MONERO_APP, LedgerWaitReason.DeviceRequest)), notices)
    }

    @Test
    fun execute_userOperationWaitsPastFirstNotice_noReminder() = runTest {
        connect(deviceAddress = ADDRESS)
        val operations = LedgerUserOperations()
        val notices = waitNotices(operations)

        gateway(operations).execute(ACCOUNT) { userOperationWaits(operations, millis = 2_000) }

        assertEquals(emptyList<LedgerWaitNotice>(), notices)
    }

    @Test
    fun isMoneroAppOpen_appInBackground_falseWithoutAsking() = runTest {
        var asked = false
        coEvery { session.openAppName(any()) } coAnswers {
            firstArg<() -> Unit>()()
            asked = true
            LEDGER_MONERO_APP
        }
        appInForeground = false

        assertFalse(gateway.isMoneroAppOpen())
        assertFalse(asked)
    }

    @Test
    fun explain_exchangeLostTransport_reportsTransportError() {
        val lost = LedgerException.TransportClosed()
        val channel = LedgerApduChannel(MoneroApp { throw lost })

        runCatching { channel.exchange(byteArrayOf(0)) }

        assertSame(lost, channel.explain(IllegalStateException("wallet2 failed")))
    }

    @Test
    fun explain_exchangeLostTransportThenBlockYields_reportsTransportError() {
        val lost = LedgerException.TransportClosed()
        val channel = LedgerApduChannel(MoneroApp { throw lost })

        runCatching { channel.exchange(byteArrayOf(0)) }

        assertSame(lost, channel.explain(LedgerSessionYieldedException()))
    }

    @Test
    fun explain_refusedOnDevice_reportsRejection() {
        val channel = LedgerApduChannel(MoneroApp { byteArrayOf(0x69, 0x82.toByte()) })

        channel.exchange(byteArrayOf(0))

        assertTrue(channel.explain(IllegalStateException("wallet2 failed")) is LedgerRejectedException)
    }

    @Test
    fun explain_refusedOnDeviceThenBlockYields_reportsSameYield() {
        val channel = LedgerApduChannel(MoneroApp { byteArrayOf(0x69, 0x82.toByte()) })
        channel.exchange(byteArrayOf(0))
        val yielded = LedgerSessionYieldedException()

        assertSame(yielded, channel.explain(yielded))
    }

    @Test
    fun explain_signerError_reportsHardwareErrorCode() {
        val channel = LedgerApduChannel(MoneroApp { error("unused") })

        val explained = channel.explain(
            ExternalSignerException(HardwareWalletErrorCode.ViewKeyExportDenied, "denied"),
        )

        assertEquals(
            HardwareWalletErrorCode.ViewKeyExportDenied,
            (explained as HardwareWalletOperationException).error,
        )
    }

    private fun gateway(userOperations: LedgerUserOperations) = MoneroLedgerOperationGateway(
        appAccess = LedgerAppAccess(session, ledgerAppKeyFetchers, storage, userOperations),
        backgroundManager = mockk { every { inForeground } answers { appInForeground } },
        dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
        userOperations = userOperations,
        connector = mockk { every { isDevicePresent } returns true },
    )

    private fun TestScope.waitNotices(operations: LedgerUserOperations): List<LedgerWaitNotice> =
        mutableListOf<LedgerWaitNotice>().also { notices ->
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { operations.waitNotices.toList(notices) }
        }

    // The block blocks the test thread like wallet2 does, so it drives virtual time itself.
    private fun TestScope.userOperationWaits(operations: LedgerUserOperations, millis: Long) {
        backgroundScope.launch { operations.run { awaitCancellation() } }
        testScheduler.advanceTimeBy(millis)
        testScheduler.runCurrent()
    }

    private fun connect(deviceAddress: String) {
        coEvery { storage.getKey(ACCOUNT.id, BlockchainType.Monero, TokenType.Native) } returns HardwarePublicKey(
            accountId = ACCOUNT.id,
            blockchainType = BlockchainType.Monero.uid,
            type = HardwarePublicKeyType.PUBLIC_KEY,
            tokenType = TokenType.Native,
            key = SecretString(ADDRESS),
            derivationPath = "m/44'/128'/0'",
            publicKey = PUBLIC_KEYS,
            derivedPublicKey = PUBLIC_KEYS,
        )
        val moneroAppOpen: suspend MockKAnswerScope<Any?, Any?>.(Call) -> Any? = {
            arg<() -> Unit>(2)()
            arg<suspend (LedgerTransport) -> Any?>(3)(
                MoneroApp {
                    transportLost?.let { throw it }
                    apdusSent++
                    PUBLIC_KEYS + deviceAddress.encodeToByteArray() + byteArrayOf(0x90.toByte(), 0)
                },
            )
        }
        coEvery { session.withApp<Any?>(LEDGER_MONERO_APP, any(), any(), any()) } coAnswers moneroAppOpen
        coEvery { session.withOpenApp<Any?>(LEDGER_MONERO_APP, any(), any(), any()) } coAnswers moneroAppOpen
    }

    // wallet2 reaches the registered channel through this bridge from native code; it is package-private.
    private fun apdusReachingDevice(action: () -> Unit): Int {
        val before = apdusSent
        action()
        return apdusSent - before
    }

    private fun walletExchange(): ByteArray {
        val bridge = Class.forName("com.piratecash.monero.signer.ExternalSignerNativeBridge")
        val generation = bridge.getDeclaredMethod("currentGeneration").apply { isAccessible = true }.invoke(null)
        val exchangeApdu = bridge.getDeclaredMethod("exchangeApdu", Long::class.java, ByteArray::class.java)
            .apply { isAccessible = true }
        try {
            return exchangeApdu.invoke(null, generation, byteArrayOf(0x00, INS_GET_KEY, 0, 0)) as ByteArray
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }

    private class MoneroApp(private val answer: (ByteArray) -> ByteArray) : LedgerTransport {
        override suspend fun exchange(apdu: ByteArray): ByteArray = answer(apdu)

        override fun close() = Unit
    }

    private object IdleChannel : ExternalSignerApduChannel {
        override fun exchange(apdu: ByteArray): ByteArray = error("unused")

        override fun cancel() = Unit
    }

    private companion object {
        const val WALLET_KEY = "wallet-key"
        const val INS_GET_KEY: Byte = 0x30
        val ADDRESS = "4" + "A".repeat(94)
        val PUBLIC_KEYS = ByteArray(64) { 1 }
        val ACCOUNT = Account(
            id = "account-id",
            name = "Ledger",
            type = AccountType.LedgerDevice(deviceId = "device", model = "NanoSPlus", walletPublicKey = WALLET_KEY),
            origin = AccountOrigin.Created,
            level = 0,
        )
    }
}
