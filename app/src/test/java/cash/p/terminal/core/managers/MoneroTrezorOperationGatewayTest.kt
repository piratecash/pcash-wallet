package cash.p.terminal.core.managers

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.trezorkit.client.TrezorSessionId
import cash.p.terminal.trezorkit.TrezorUsbDisconnectedException
import cash.p.terminal.trezorkit.TrezorUsbOperationTimeoutException
import cash.p.terminal.trezorkit.TrezorUsbShortPacketException
import cash.p.terminal.trezorkit.protocol.DecodedMessage
import cash.p.terminal.trezorkit.protocol.FramingCodec
import cash.p.terminal.trezorkit.protocol.FramingDecoder
import cash.p.terminal.trezorkit.protocol.TrezorPromptPolicy
import cash.p.terminal.trezorkit.transport.TrezorRawUsbChannel
import cash.p.terminal.trezorkit.transport.TrezorUsbCoordinator
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import com.google.protobuf.ByteString
import com.piratecash.monero.signer.ExternalSignerException
import com.piratecash.monero.signer.ExternalSignerRegistration
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import com.satoshilabs.trezor.lib.protobuf.TrezorMessage.MessageType
import com.satoshilabs.trezor.lib.protobuf.TrezorMessageBitcoin.PublicKey
import com.satoshilabs.trezor.lib.protobuf.TrezorMessageCommon.HDNodeType
import com.satoshilabs.trezor.lib.protobuf.TrezorMessageManagement.Features
import com.satoshilabs.trezor.lib.protobuf.TrezorMessageManagement.Initialize
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class MoneroTrezorOperationGatewayTest {

    @Test
    fun execute_callerCancelled_rethrowsStructuredCancellation() = runTest {
        val cancellation = CancellationException("cancelled")
        val coordinator = mockk<TrezorUsbCoordinator> {
            coEvery { withRawChannel<String>(any()) } throws cancellation
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val gateway = MoneroTrezorOperationGateway(
            coordinator = coordinator,
            readiness = mockk(),
            dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
            accountManager = knownAccounts(),
        )

        val actual = assertFailsWith<CancellationException> {
            gateway.execute { "unused" }
        }

        assertSame(cancellation, actual)
    }

    @Test
    fun execute_externalSignerFailure_returnsUnifiedHardwareFailure() = runTest {
        val coordinator = mockk<TrezorUsbCoordinator> {
            coEvery { withRawChannel<String>(any()) } throws ExternalSignerException(
                HardwareWalletErrorCode.Disconnected,
                "disconnected",
            )
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val gateway = MoneroTrezorOperationGateway(
            coordinator = coordinator,
            readiness = mockk(),
            dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
            accountManager = knownAccounts(),
        )

        val error = assertFailsWith<HardwareWalletOperationException> {
            gateway.execute { "unused" }
        }

        assertEquals(HardwareWalletErrorCode.Disconnected, error.error)
        assertTrue(error.cause is ExternalSignerException)
    }

    @Test
    fun execute_externalSignerCancellation_preservesCancellationCode() = runTest {
        val coordinator = mockk<TrezorUsbCoordinator> {
            coEvery { withRawChannel<String>(any()) } throws ExternalSignerException(
                HardwareWalletErrorCode.Cancelled,
                "cancelled",
            )
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val gateway = MoneroTrezorOperationGateway(
            coordinator = coordinator,
            readiness = mockk(),
            dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
            accountManager = knownAccounts(),
        )

        val error = assertFailsWith<HardwareWalletOperationException> {
            gateway.execute { "unused" }
        }

        assertEquals(HardwareWalletErrorCode.Cancelled, error.error)
    }

    @Test
    fun execute_nativeOwnershipRetained_preservesCleanupSignal() = runTest {
        val ownershipRetained = MoneroDeviceWalletOwnershipRetainedException(
            HardwareWalletOperationException(
                HardwareWalletErrorCode.Protocol,
                "close failed",
            ),
        )
        val coordinator = mockk<TrezorUsbCoordinator> {
            coEvery { withRawChannel<String>(any()) } throws ownershipRetained
        }
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val gateway = MoneroTrezorOperationGateway(
            coordinator = coordinator,
            readiness = mockk(),
            dispatcherProvider = TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
            accountManager = knownAccounts(),
        )

        val actual = assertFailsWith<MoneroDeviceWalletOwnershipRetainedException> {
            gateway.execute { "unused" }
        }

        assertSame(ownershipRetained, actual)
    }

    @Test
    fun execute_legacyAccount_verifiesLiveWalletIdentityBeforeBinding() = runTest {
        val channel = scriptedChannel(LIVE_WALLET_KEY)
        val coordinator = coordinator(channel)
        val sessionId = TrezorSessionId(SESSION_ID)
        val readiness = readiness(sessionId)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val gateway = MoneroTrezorOperationGateway(
            coordinator,
            readiness,
            TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
            knownAccounts(),
        )

        val result = gateway.execute(legacyAccount()) { it }

        assertEquals(LIVE_WALLET_KEY, result)
        assertFailsWith<IllegalStateException> { sessionId.toByteArray() }
        coVerify(exactly = 1) {
            readiness.requireWallet(
                match { it.id == ACCOUNT_ID },
                LIVE_WALLET_KEY,
            )
        }
    }

    @Test
    fun execute_featuresBeforeWalletPublicKey_usesNewestSessionAndBindsWallet() = runTest {
        val sessionId = TrezorSessionId(NEWEST_SESSION_ID)
        val readiness = readiness(sessionId)
        every { readiness.requireSession(any()) } throws IllegalStateException("Expected newest features")
        every {
            readiness.requireSession(
                match { it.sessionId?.toByteArray()?.contentEquals(NEWEST_SESSION_ID) == true },
            )
        } returns sessionId
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val gateway = MoneroTrezorOperationGateway(
            coordinator(scriptedChannel(LIVE_WALLET_KEY, NEWEST_SESSION_ID)),
            readiness,
            TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
            knownAccounts(),
        )

        val result = gateway.execute(legacyAccount()) { it }

        assertEquals(LIVE_WALLET_KEY, result)
        coVerify(exactly = 1) {
            readiness.requireWallet(
                match { it.id == ACCOUNT_ID },
                LIVE_WALLET_KEY,
            )
        }
    }

    @Test
    fun execute_boundAccountWrongWallet_rejectsBinding() = runTest {
        val channel = scriptedChannel(LIVE_WALLET_KEY)
        val coordinator = coordinator(channel)
        val sessionId = TrezorSessionId(SESSION_ID)
        val readiness = readiness(sessionId)
        val account = legacyAccount().copy(
            type = (legacyAccount().type as AccountType.TrezorDevice).copy(
                walletPublicKey = "different-wallet",
            ),
        )
        coEvery {
            readiness.requireWallet(account, LIVE_WALLET_KEY)
        } throws HardwareWalletOperationException(
            HardwareWalletErrorCode.WrongWallet,
            "wrong wallet",
        )
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val gateway = MoneroTrezorOperationGateway(
            coordinator,
            readiness,
            TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
            knownAccounts(),
        )
        var blockInvoked = false

        val error = assertFailsWith<HardwareWalletOperationException> {
            gateway.execute(account) {
                blockInvoked = true
                "unused"
            }
        }

        assertEquals(HardwareWalletErrorCode.WrongWallet, error.error)
        assertFalse(blockInvoked)
        assertFailsWith<IllegalStateException> { sessionId.toByteArray() }
    }

    @Test
    fun withExternalSignerRegistration_success_releasesRegistration() = runTest {
        val registration = TestRegistration()

        withExternalSignerRegistration(registration) {}

        assertEquals(1, registration.releaseCount)
    }

    @Test
    fun withExternalSignerRegistration_operationFails_releasesAndRethrowsOperationFailure() = runTest {
        val operationFailure = IllegalStateException("operation")
        val registration = TestRegistration()

        val actual = captureFailure {
            withExternalSignerRegistration(registration) {
                throw operationFailure
            }
        }

        assertSame(operationFailure, actual)
        assertEquals(1, registration.releaseCount)
    }

    @Test
    fun withExternalSignerRegistration_releaseFails_rethrowsReleaseFailure() = runTest {
        val releaseFailure = IllegalStateException("release")
        val registration = TestRegistration(releaseFailure)

        val actual = captureFailure {
            withExternalSignerRegistration(registration) {}
        }

        assertSame(releaseFailure, actual)
    }

    @Test
    fun withExternalSignerRegistration_operationAndReleaseFail_suppressesReleaseFailure() = runTest {
        val operationFailure = IllegalStateException("operation")
        val releaseFailure = IllegalStateException("release")
        val registration = TestRegistration(releaseFailure)

        val actual = captureFailure {
            withExternalSignerRegistration(registration) {
                throw operationFailure
            }
        }

        assertSame(operationFailure, actual)
        assertEquals(listOf(releaseFailure), actual.suppressed.toList())
    }

    @Test
    fun withExternalSignerRegistration_callerCancelled_signalsBeforeRelease() = runTest {
        val registration = TestRegistration()
        val operation = launch(start = CoroutineStart.UNDISPATCHED) {
            withExternalSignerRegistration(registration) {
                awaitCancellation()
            }
        }

        operation.cancelAndJoin()

        assertEquals(1, registration.signalCancellationCount)
        assertEquals(1, registration.releaseCount)
    }

    @Test
    fun execute_preflightReadBlocked_cancellationInterruptsRawChannel() = runTest {
        val readStarted = CountDownLatch(1)
        val readInterrupted = CountDownLatch(1)
        val channel = object : TrezorRawUsbChannel {
            override fun writePacket(packet: ByteArray) = Unit

            override fun readPacket(): ByteArray {
                readStarted.countDown()
                try {
                    CountDownLatch(1).await()
                } catch (error: InterruptedException) {
                    readInterrupted.countDown()
                    throw error
                }
                error("Unreachable")
            }

            override fun readPacket(deadlineNanos: Long): ByteArray = readPacket()

            override fun cancel() = Unit
        }
        val gateway = MoneroTrezorOperationGateway(
            coordinator(channel),
            mockk(),
            TestDispatcherProvider(Dispatchers.IO, CoroutineScope(Dispatchers.IO)),
            knownAccounts(),
        )
        val operation = launch(start = CoroutineStart.UNDISPATCHED) {
            gateway.execute { "unused" }
        }
        assertTrue(readStarted.await(1, TimeUnit.SECONDS))

        operation.cancelAndJoin()

        assertTrue(readInterrupted.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun externalSignerChannel_packetTimeout_preservesHardwareError() {
        assertChannelFailure(
            TrezorUsbOperationTimeoutException(),
            HardwareWalletErrorCode.PacketTimeout,
        )
    }

    @Test
    fun externalSignerChannel_disconnected_preservesHardwareError() {
        assertChannelFailure(
            TrezorUsbDisconnectedException(),
            HardwareWalletErrorCode.Disconnected,
        )
    }

    @Test
    fun externalSignerChannel_shortPacket_preservesHardwareError() {
        assertChannelFailure(
            TrezorUsbShortPacketException("read", 32),
            HardwareWalletErrorCode.ShortPacket,
        )
    }

    @Test
    fun executeUnattended_noConfirmedSession_failsWithoutOpeningDevice() = runTest {
        val coordinator = mockk<TrezorUsbCoordinator>()
        val gateway = gateway(coordinator, readiness())

        val error = assertFailsWith<HardwareWalletOperationException> {
            gateway.executeUnattended(legacyAccount()) { it }
        }

        assertEquals(HardwareWalletErrorCode.PendingDeviceVerification, error.error)
        coVerify(exactly = 0) { coordinator.withRawChannel<String>(any()) }
        coVerify(exactly = 0) { coordinator.withPermittedRawChannel<String>(any()) }
    }

    @Test
    fun executeUnattended_confirmedSession_resumesItOverPermittedChannel() = runTest {
        val unattendedChannel = scriptedChannel(LIVE_WALLET_KEY)
        val coordinator = coordinator(scriptedChannel(LIVE_WALLET_KEY), unattendedChannel)
        val gateway = gateway(coordinator, readiness())
        gateway.execute(legacyAccount()) { it }
        gateway.markLiveRefreshConfirmed(legacyAccount())

        val result = gateway.executeUnattended(legacyAccount()) { it }

        assertEquals(LIVE_WALLET_KEY, result)
        val initialize = unattendedChannel.writtenMessages().first()
        assertEquals(MessageType.MessageType_Initialize_VALUE, initialize.msgType)
        assertTrue(Initialize.parseFrom(initialize.payload).sessionId.toByteArray().contentEquals(SESSION_ID))
        coVerify(exactly = 1) { coordinator.withRawChannel<String>(any()) }
    }

    @Test
    fun executeUnattended_lockedDevice_failsBeforeWalletKeyAndNeedsNewConfirmation() = runTest {
        val unattendedChannel = scriptedChannel(LIVE_WALLET_KEY, unlocked = false)
        val gateway = gateway(coordinator(scriptedChannel(LIVE_WALLET_KEY), unattendedChannel), readiness())
        gateway.execute(legacyAccount()) { it }
        gateway.markLiveRefreshConfirmed(legacyAccount())

        val locked = assertFailsWith<HardwareWalletOperationException> {
            gateway.executeUnattended(legacyAccount()) { it }
        }
        val next = assertFailsWith<HardwareWalletOperationException> {
            gateway.executeUnattended(legacyAccount()) { it }
        }

        assertEquals(HardwareWalletErrorCode.PendingDeviceVerification, locked.error)
        assertEquals(HardwareWalletErrorCode.PendingDeviceVerification, next.error)
        assertFalse(unattendedChannel.requestedWalletKey())
    }

    @Test
    fun executeUnattended_resumedSessionIdDiffers_failsBeforeWalletKey() = runTest {
        val unattendedChannel = scriptedChannel(LIVE_WALLET_KEY, sessionId = NEWEST_SESSION_ID)
        val gateway = gateway(coordinator(scriptedChannel(LIVE_WALLET_KEY), unattendedChannel), readiness())
        gateway.execute(legacyAccount()) { it }
        gateway.markLiveRefreshConfirmed(legacyAccount())

        val error = assertFailsWith<HardwareWalletOperationException> {
            gateway.executeUnattended(legacyAccount()) { it }
        }

        assertEquals(HardwareWalletErrorCode.PendingDeviceVerification, error.error)
        assertFalse(unattendedChannel.requestedWalletKey())
    }

    @Test
    fun retainSessions_trezorDetached_needsNewConfirmation() = runTest {
        val coordinator = coordinator(scriptedChannel(LIVE_WALLET_KEY))
        val gateway = gateway(coordinator, readiness())
        gateway.execute(legacyAccount()) { it }
        gateway.markLiveRefreshConfirmed(legacyAccount())

        gateway.retainSessions(emptySet())

        val error = assertFailsWith<HardwareWalletOperationException> {
            gateway.executeUnattended(legacyAccount()) { it }
        }
        assertEquals(HardwareWalletErrorCode.PendingDeviceVerification, error.error)
        coVerify(exactly = 0) { coordinator.withPermittedRawChannel<String>(any()) }
    }

    @Test
    fun retainSessions_otherAccountDeleted_keepsRemainingSession() = runTest {
        val deleted = legacyAccount().copy(id = "deleted-account")
        val coordinator = queuedCoordinator(*Array(3) { scriptedChannel(LIVE_WALLET_KEY) })
        val gateway = gateway(coordinator, readiness())
        listOf(legacyAccount(), deleted).forEach { account ->
            gateway.execute(account) { it }
            gateway.markLiveRefreshConfirmed(account)
        }

        gateway.retainSessions(setOf(ACCOUNT_ID))

        assertEquals(LIVE_WALLET_KEY, gateway.executeUnattended(legacyAccount()) { it })
        val error = assertFailsWith<HardwareWalletOperationException> {
            gateway.executeUnattended(deleted) { it }
        }
        assertEquals(HardwareWalletErrorCode.PendingDeviceVerification, error.error)
    }

    @Test
    fun executeUnattended_accountDeletedDuringVerification_keepsNoSession() = runTest {
        val deleted = mutableSetOf<String>()
        val readiness = readiness()
        val nextAttended = scriptedChannel(LIVE_WALLET_KEY)
        val coordinator = queuedCoordinator(
            scriptedChannel(LIVE_WALLET_KEY),
            scriptedChannel(LIVE_WALLET_KEY),
            nextAttended,
        )
        val gateway = gateway(coordinator, readiness, knownAccounts(deleted))
        gateway.execute(legacyAccount()) { it }
        gateway.markLiveRefreshConfirmed(legacyAccount())
        coEvery { readiness.requireWallet(any(), any()) } answers {
            deleted += ACCOUNT_ID
            gateway.retainSessions(emptySet())
        }

        gateway.executeUnattended(legacyAccount()) { it }
        gateway.execute(legacyAccount()) { it }

        val initialize = nextAttended.writtenMessages().first()
        assertTrue(Initialize.parseFrom(initialize.payload).sessionId.isEmpty)
    }

    @Test
    fun executeUnattended_accountDeletedAfterPresenceCheck_keepsNoSession() = runTest {
        val nextAttended = scriptedChannel(LIVE_WALLET_KEY)
        val coordinator = queuedCoordinator(
            scriptedChannel(LIVE_WALLET_KEY),
            scriptedChannel(LIVE_WALLET_KEY),
            nextAttended,
        )
        lateinit var gateway: MoneroTrezorOperationGateway
        var deleteOnNextLookup = false
        var deleted = false
        // The deletion and its wipe land after remember's presence check, before its map is published.
        val accountManager = mockk<IAccountManager> {
            every { account(any()) } answers {
                if (deleteOnNextLookup) {
                    deleteOnNextLookup = false
                    deleted = true
                    gateway.retainSessions(emptySet())
                    legacyAccount()
                } else {
                    legacyAccount().takeUnless { deleted }
                }
            }
        }
        gateway = gateway(coordinator, readiness(), accountManager)
        gateway.execute(legacyAccount()) { it }
        gateway.markLiveRefreshConfirmed(legacyAccount())
        deleteOnNextLookup = true

        gateway.executeUnattended(legacyAccount()) { it }
        gateway.execute(legacyAccount()) { it }

        val initialize = nextAttended.writtenMessages().first()
        assertTrue(Initialize.parseFrom(initialize.payload).sessionId.isEmpty)
    }

    @Test
    fun execute_accountDeletedThenOtherSessionForgotten_keepsNoSession() = runTest {
        val other = legacyAccount().copy(id = "other-account")
        val unplugged = mockk<TrezorRawUsbChannel>(relaxed = true) {
            every { writePacket(any()) } throws ExternalSignerException(HardwareWalletErrorCode.Disconnected, "gone")
        }
        val nextAttended = scriptedChannel(LIVE_WALLET_KEY)
        val coordinator = queuedCoordinator(scriptedChannel(LIVE_WALLET_KEY), unplugged, nextAttended)
        lateinit var gateway: MoneroTrezorOperationGateway
        var deleteOnNextLookup = true
        var deleted = false
        // After remember's presence check: delete + retain, then another account's forget re-publishes an empty map.
        val accountManager = mockk<IAccountManager> {
            every { account(any()) } answers {
                if (deleteOnNextLookup) {
                    deleteOnNextLookup = false
                    deleted = true
                    gateway.retainSessions(emptySet())
                    val forgotten = runBlocking {
                        assertFailsWith<HardwareWalletOperationException> { gateway.execute(other) { it } }
                    }
                    assertEquals(HardwareWalletErrorCode.Disconnected, forgotten.error)
                    legacyAccount()
                } else {
                    legacyAccount().takeUnless { deleted }
                }
            }
        }
        gateway = gateway(coordinator, readiness(), accountManager)

        gateway.execute(legacyAccount()) { it }
        gateway.execute(legacyAccount()) { it }

        val initialize = nextAttended.writtenMessages().first()
        assertTrue(Initialize.parseFrom(initialize.payload).sessionId.isEmpty)
    }

    @Test
    fun executeUnattended_afterSessionEndingFailure_needsNewConfirmation() = runTest {
        listOf(HardwareWalletErrorCode.Disconnected, HardwareWalletErrorCode.WrongDevice).forEach { code ->
            val coordinator = coordinator(scriptedChannel(LIVE_WALLET_KEY))
            val gateway = gateway(coordinator, readiness())
            gateway.execute(legacyAccount()) { it }
            gateway.markLiveRefreshConfirmed(legacyAccount())
            coEvery { coordinator.withRawChannel<String>(any()) } throws ExternalSignerException(code, "ended")
            assertFailsWith<HardwareWalletOperationException> { gateway.execute(legacyAccount()) { it } }

            val error = assertFailsWith<HardwareWalletOperationException> {
                gateway.executeUnattended(legacyAccount()) { it }
            }

            assertEquals(HardwareWalletErrorCode.PendingDeviceVerification, error.error)
            coVerify(exactly = 0) { coordinator.withPermittedRawChannel<String>(any()) }
        }
    }

    @Test
    fun externalSignerChannel_unattendedDevicePrompt_cancelsItAndFails() {
        listOf(
            MessageType.MessageType_ButtonRequest_VALUE,
            MessageType.MessageType_PinMatrixRequest_VALUE,
            MessageType.MessageType_PassphraseRequest_VALUE,
        ).forEach { prompt ->
            val raw = ScriptedRawChannel(
                FramingCodec.encode(prompt, ByteArray(0)) +
                    FramingCodec.encode(MessageType.MessageType_Failure_VALUE, ByteArray(0)),
            )

            val error = assertFailsWith<ExternalSignerException> {
                TrezorExternalSignerChannel(raw, TrezorPromptPolicy.Unattended).readPacket(0)
            }

            assertEquals(HardwareWalletErrorCode.PendingDeviceVerification, error.hardwareErrorCode)
            assertEquals(listOf(MessageType.MessageType_Cancel_VALUE), raw.writtenMessages().map { it.msgType })
        }
    }

    @Test
    fun externalSignerChannel_interactiveDevicePrompt_passesThrough() {
        val prompt = FramingCodec.encode(MessageType.MessageType_ButtonRequest_VALUE, ByteArray(0))
        val raw = ScriptedRawChannel(prompt)

        val packet = TrezorExternalSignerChannel(raw).readPacket(0)

        assertTrue(packet.contentEquals(prompt.single()))
        assertTrue(raw.writtenMessages().isEmpty())
    }

    private fun TestScope.gateway(
        coordinator: TrezorUsbCoordinator,
        readiness: MoneroTrezorReadiness,
        accountManager: IAccountManager = knownAccounts(),
    ) = UnconfinedTestDispatcher(testScheduler).let { dispatcher ->
        MoneroTrezorOperationGateway(
            coordinator,
            readiness,
            TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher)),
            accountManager,
        )
    }

    private fun knownAccounts(deleted: Set<String> = emptySet()) = mockk<IAccountManager> {
        every { account(any()) } answers { legacyAccount().copy(id = firstArg()).takeUnless { it.id in deleted } }
    }

    private fun queuedCoordinator(vararg channels: TrezorRawUsbChannel): TrezorUsbCoordinator {
        val queue = ArrayDeque(channels.toList())
        return mockk {
            coEvery { withRawChannel<String>(any()) } coAnswers {
                firstArg<suspend (TrezorRawUsbChannel) -> String>().invoke(queue.removeFirst())
            }
            coEvery { withPermittedRawChannel<String>(any()) } coAnswers {
                firstArg<suspend (TrezorRawUsbChannel) -> String>().invoke(queue.removeFirst())
            }
        }
    }

    private fun assertChannelFailure(
        cause: Exception,
        expected: HardwareWalletErrorCode,
    ) {
        val rawChannel = mockk<TrezorRawUsbChannel> {
            every { writePacket(any()) } throws cause
        }

        val error = assertFailsWith<ExternalSignerException> {
            TrezorExternalSignerChannel(rawChannel).writePacket(ByteArray(64))
        }

        assertEquals(expected, error.hardwareErrorCode)
        assertSame(cause, error.cause)
    }

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable =
        try {
            block()
            fail("Expected failure")
            throw AssertionError("unreachable")
        } catch (error: Throwable) {
            error
        }

    private fun coordinator(
        channel: TrezorRawUsbChannel,
        permittedChannel: TrezorRawUsbChannel? = null,
    ) = mockk<TrezorUsbCoordinator> {
        coEvery { withRawChannel<String>(any()) } coAnswers {
            firstArg<suspend (TrezorRawUsbChannel) -> String>().invoke(channel)
        }
        coEvery { withPermittedRawChannel<String>(any()) } coAnswers {
            firstArg<suspend (TrezorRawUsbChannel) -> String>().invoke(checkNotNull(permittedChannel))
        }
    }

    private fun readiness(
        sessionId: TrezorSessionId = TrezorSessionId(SESSION_ID),
    ) = mockk<MoneroTrezorReadiness> {
        every { requireLive(any<TrezorFeatures>(), any()) } answers { firstArg() }
        every { requireSession(any()) } returns sessionId andThenAnswer { TrezorSessionId(SESSION_ID) }
        coEvery { requireWallet(any(), any()) } just Runs
    }

    private fun scriptedChannel(
        walletKey: String,
        extraFeaturesSessionId: ByteArray? = null,
        unlocked: Boolean = true,
        sessionId: ByteArray = SESSION_ID,
    ): ScriptedRawChannel {
        val responses = featuresResponse(sessionId, unlocked)
        extraFeaturesSessionId?.let { responses += featuresResponse(it) }
        val publicKey = PublicKey.newBuilder()
            .setXpub(walletKey)
            .setNode(
                HDNodeType.newBuilder()
                    .setDepth(0)
                    .setFingerprint(0)
                    .setChildNum(0)
                    .setPublicKey(ByteString.copyFrom(byteArrayOf(2, 3)))
                    .setChainCode(ByteString.copyFrom(byteArrayOf(4, 5)))
                    .build(),
            )
            .build()
        responses += FramingCodec.encode(MessageType.MessageType_PublicKey_VALUE, publicKey.toByteArray())
        return ScriptedRawChannel(responses)
    }

    private fun featuresResponse(sessionId: ByteArray, unlocked: Boolean = true): MutableList<ByteArray> {
        val features = Features.newBuilder()
            .setUnlocked(unlocked)
            .setDeviceId("device-id")
            .setInternalModel("T3T1")
            .setMajorVersion(2)
            .setMinorVersion(8)
            .setPatchVersion(10)
            .setSessionId(ByteString.copyFrom(sessionId))
            .build()
        return FramingCodec.encode(MessageType.MessageType_Features_VALUE, features.toByteArray()).toMutableList()
    }

    private fun legacyAccount() = Account(
        id = ACCOUNT_ID,
        name = "Legacy Safe 5",
        type = AccountType.TrezorDevice(
            deviceId = "device-id",
            model = "T3T1",
            firmwareVersion = "2.8.10",
            walletPublicKey = "",
        ),
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = false,
    )

    private class TestRegistration(
        private val releaseFailure: Throwable? = null,
    ) : ExternalSignerRegistration {
        var releaseCount = 0
            private set
        var signalCancellationCount = 0
            private set

        override fun signalCancellation() {
            signalCancellationCount++
        }

        override fun cancel() = Unit

        override fun release() {
            releaseCount++
            releaseFailure?.let { throw it }
        }
    }

    private class ScriptedRawChannel(
        responses: List<ByteArray>,
    ) : TrezorRawUsbChannel {
        private val responses = ArrayDeque(responses)
        private val written = mutableListOf<ByteArray>()

        fun writtenMessages(): List<DecodedMessage> {
            val decoder = FramingDecoder()
            return written.mapNotNull(decoder::offer)
        }

        fun requestedWalletKey(): Boolean =
            writtenMessages().any { it.msgType == MessageType.MessageType_GetPublicKey_VALUE }

        override fun writePacket(packet: ByteArray) {
            written += packet
        }

        override fun readPacket(): ByteArray = responses.removeFirst()

        override fun readPacket(deadlineNanos: Long): ByteArray = readPacket()

        override fun cancel() = Unit
    }

    private companion object {
        const val ACCOUNT_ID = "account-id"
        const val LIVE_WALLET_KEY = "zpub-live-wallet"
        val SESSION_ID = byteArrayOf(1, 2, 3, 4)
        val NEWEST_SESSION_ID = byteArrayOf(5, 6, 7, 8)
    }
}
