package cash.p.terminal.core.managers

import cash.p.terminal.core.tryOrNull
import cash.p.terminal.trezor.client.TrezorPublicKeySpecs
import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.trezorkit.client.TrezorSessionId
import cash.p.terminal.trezorkit.client.UsbTrezorClient
import cash.p.terminal.trezorkit.protocol.FramingCodec
import cash.p.terminal.trezorkit.protocol.FramingDecoder
import cash.p.terminal.trezorkit.protocol.MessageTypeRegistry
import cash.p.terminal.trezorkit.protocol.TrezorPromptPolicy
import cash.p.terminal.trezorkit.protocol.TrezorSession
import cash.p.terminal.trezorkit.transport.ITrezorTransport
import cash.p.terminal.trezorkit.transport.TrezorRawUsbChannel
import cash.p.terminal.trezorkit.transport.TrezorUsbCoordinator
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import com.piratecash.monero.signer.ExternalSigner
import com.piratecash.monero.signer.ExternalSignerChannel
import com.piratecash.monero.signer.ExternalSignerException
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import com.satoshilabs.trezor.lib.protobuf.TrezorMessage.MessageType
import com.satoshilabs.trezor.lib.protobuf.TrezorMessageManagement.Cancel
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The only app-layer bridge that knows both the Trezor and Monero kit contracts.
 */
class MoneroTrezorOperationGateway internal constructor(
    private val coordinator: TrezorUsbCoordinator,
    private val readiness: MoneroTrezorReadiness,
    private val dispatcherProvider: DispatcherProvider,
    accountManager: IAccountManager,
) : MoneroHardwareGateway {
    private val sessions = TrezorSessionCache(accountManager)

    override val isUserOperationWaiting: Boolean
        get() = coordinator.isUserOperationWaiting

    suspend fun <T> execute(block: () -> T): T =
        executeInternal(null, TrezorPromptPolicy.Interactive) { block() }

    override suspend fun <T> execute(
        account: Account,
        block: (String) -> T,
    ): T = executeInternal(account, TrezorPromptPolicy.Interactive, block)

    override suspend fun <T> executeUnattended(
        account: Account,
        block: (String) -> T,
    ): T {
        sessions.requireConfirmed(account.id)
        return executeInternal(account, TrezorPromptPolicy.Unattended, block)
    }

    override fun markLiveRefreshConfirmed(account: Account) = sessions.markConfirmed(account.id)

    override fun retainSessions(accountIds: Set<String>) = sessions.retain(accountIds)

    private suspend fun <T> executeInternal(
        account: Account?,
        promptPolicy: TrezorPromptPolicy,
        block: (String) -> T,
    ): T {
        try {
            return withChannel(promptPolicy) { channel ->
                val device = verifyDevice(channel, account, promptPolicy)
                val registration = try {
                    account?.let { sessions.remember(it.id, device.deviceId, device.sessionId) }
                    ExternalSigner.register(
                        TrezorExternalSignerChannel(channel, promptPolicy),
                        device.sessionId,
                    )
                } finally {
                    device.sessionId.fill(0)
                }
                withExternalSignerRegistration(registration) {
                    runInterruptible(dispatcherProvider.io) {
                        block(device.walletPublicKey)
                    }
                }
            }
        } catch (error: Throwable) {
            val failure = mapFailure(error)
            account?.let { sessions.onFailure(it.id, failure) }
            throw failure
        }
    }

    private suspend fun <T> withChannel(
        promptPolicy: TrezorPromptPolicy,
        block: suspend (TrezorRawUsbChannel) -> T,
    ): T = when (promptPolicy) {
        TrezorPromptPolicy.Interactive -> coordinator.withRawChannel(block)
        TrezorPromptPolicy.Unattended -> coordinator.withPermittedRawChannel(block)
    }

    private fun mapFailure(error: Throwable): Throwable =
        when (error) {
            is ExternalSignerException -> HardwareWalletOperationException(
                error.hardwareErrorCode,
                error.message,
            ).also { it.initCause(error) }
            is MoneroDeviceWalletOwnershipRetainedException,
            is HardwareWalletOperationException,
            is CancellationException,
            -> error
            else -> readiness.hardwareFailure(error)
        }

    private suspend fun verifyDevice(
        channel: TrezorRawUsbChannel,
        account: Account?,
        promptPolicy: TrezorPromptPolicy,
    ): VerifiedDevice {
        val accountType = account?.requireTrezorType()
        val read = readDevice(channel, account, promptPolicy)
        try {
            readiness.requireLive(read.features, accountType)
            if (account != null) {
                readiness.requireWallet(account, read.walletPublicKey)
            }
            return VerifiedDevice(
                deviceId = read.features.deviceId,
                walletPublicKey = read.walletPublicKey,
                sessionId = read.sessionId.toByteArray(),
            )
        } finally {
            read.sessionId.close()
        }
    }

    private suspend fun readDevice(
        channel: TrezorRawUsbChannel,
        account: Account?,
        promptPolicy: TrezorPromptPolicy,
    ): DeviceRead {
        val transport = BorrowedRawChannelTransport(channel, dispatcherProvider.io)
        val session = TrezorSession(transport, MessageTypeRegistry, dispatcherProvider.io, promptPolicy)
        val resumeSessionId = account?.let { sessions.sessionIdOf(it.id) }
        try {
            return UsbTrezorClient(session).connect(resumeSessionId) {
                var features = getFeatures()
                try {
                    if (promptPolicy == TrezorPromptPolicy.Unattended) {
                        sessions.requireResumed(requireNotNull(account).id, features)
                    }
                    val walletPublicKey = if (account != null) {
                        val key = getPublicKeys(listOf(TrezorPublicKeySpecs.walletIdentityRequest)).single().key
                        features = getFeatures()
                        key
                    } else {
                        ""
                    }
                    DeviceRead(
                        features = features,
                        walletPublicKey = walletPublicKey,
                        sessionId = readiness.requireSession(features),
                    )
                } catch (error: Throwable) {
                    features.sessionId?.close()
                    throw error
                }
            }
        } finally {
            resumeSessionId?.fill(0)
        }
    }

    private fun Account.requireTrezorType(): AccountType.TrezorDevice =
        type as? AccountType.TrezorDevice
            ?: throw HardwareWalletOperationException(
                HardwareWalletErrorCode.ProvisioningTargetMismatch,
                "Monero device wallet requires a Trezor account",
            )

    private data class VerifiedDevice(
        val deviceId: String?,
        val walletPublicKey: String,
        val sessionId: ByteArray,
    )

    private data class DeviceRead(
        val features: TrezorFeatures,
        val walletPublicKey: String,
        val sessionId: TrezorSessionId,
    )
}

private class BorrowedRawChannelTransport(
    private val channel: TrezorRawUsbChannel,
    private val dispatcher: CoroutineDispatcher,
) : ITrezorTransport {
    override suspend fun open() = Unit

    override fun close() = Unit

    override suspend fun writePacket(packet: ByteArray) = runInterruptible(dispatcher) {
        channel.writePacket(packet)
    }

    override suspend fun readPacket(): ByteArray = runInterruptible(dispatcher) {
        channel.readPacket()
    }
}

/** In memory only: the session id is never persisted or logged. */
private class TrezorSessionCache(private val accountManager: IAccountManager) {
    private class Entry(
        val deviceId: String?,
        val sessionId: ByteArray,
        val liveRefreshConfirmed: Boolean,
    )

    // A fresh holder per publication, so a CAS cannot succeed against an equal-looking older map (ABA).
    private class Snapshot(val entries: Map<String, Entry>)

    private val snapshot = AtomicReference(Snapshot(emptyMap()))

    fun sessionIdOf(accountId: String): ByteArray? = snapshot.get().entries[accountId]?.sessionId?.copyOf()

    fun remember(accountId: String, deviceId: String?, sessionId: ByteArray) {
        val copy = sessionId.copyOf()
        update(copy) { map ->
            if (accountManager.account(accountId) == null) return@update map - accountId
            val old = map[accountId]
            val sameSession = old != null && old.deviceId == deviceId && old.sessionId.contentEquals(copy)
            map + (accountId to Entry(deviceId, copy, sameSession && old.liveRefreshConfirmed))
        }
    }

    fun markConfirmed(accountId: String) = setConfirmed(accountId, true)

    fun requireConfirmed(accountId: String) {
        if (snapshot.get().entries[accountId]?.liveRefreshConfirmed != true) throw pendingDeviceVerification()
    }

    /** Unattended work may only continue the confirmed session of the same unlocked device. */
    fun requireResumed(accountId: String, features: TrezorFeatures) {
        val entry = snapshot.get().entries[accountId]
        val resumedId = features.sessionId?.toByteArray()
        val resumed = entry != null &&
            entry.liveRefreshConfirmed &&
            features.unlocked &&
            features.deviceId == entry.deviceId &&
            resumedId?.contentEquals(entry.sessionId) == true
        resumedId?.fill(0)
        if (!resumed) throw pendingDeviceVerification()
    }

    fun onFailure(accountId: String, failure: Throwable) {
        val code = (failure as? HardwareWalletOperationException)?.error ?: return
        when (code) {
            in SESSION_ENDING_FAILURES -> forget(accountId)
            HardwareWalletErrorCode.PendingDeviceVerification -> setConfirmed(accountId, false)
            else -> Unit
        }
    }

    fun retain(accountIds: Set<String>) = update(null) { map -> map.filterKeys(accountIds::contains) }

    private fun forget(accountId: String) = update(null) { map -> map - accountId }

    private fun setConfirmed(accountId: String, confirmed: Boolean) = update(null) { map ->
        val entry = map[accountId] ?: return@update map
        map + (accountId to Entry(entry.deviceId, entry.sessionId, confirmed))
    }

    /** [transform] may rerun on contention, so session ids are zeroed only once out of the published map. */
    private fun update(created: ByteArray?, transform: (Map<String, Entry>) -> Map<String, Entry>) {
        var old: Snapshot
        var new: Snapshot
        do {
            old = snapshot.get()
            new = Snapshot(transform(old.entries))
        } while (!snapshot.compareAndSet(old, new))
        (old.entries.values.map(Entry::sessionId) + listOfNotNull(created))
            .filter { dropped -> new.entries.values.none { it.sessionId === dropped } }
            .forEach { it.fill(0) }
    }

    private fun pendingDeviceVerification() = HardwareWalletOperationException(
        HardwareWalletErrorCode.PendingDeviceVerification,
        "Trezor session must be confirmed on the device",
    )

    private companion object {
        // After any of these the attached device may be another one, so its session is no longer trusted.
        val SESSION_ENDING_FAILURES = setOf(
            HardwareWalletErrorCode.DeviceNotFound,
            HardwareWalletErrorCode.UsbOpenFailed,
            HardwareWalletErrorCode.UsbInterfaceUnavailable,
            HardwareWalletErrorCode.PacketTimeout,
            HardwareWalletErrorCode.Disconnected,
            HardwareWalletErrorCode.StaleLease,
            HardwareWalletErrorCode.ShortPacket,
            HardwareWalletErrorCode.WrongDevice,
            HardwareWalletErrorCode.WrongWallet,
        )
    }
}

internal class TrezorExternalSignerChannel(
    private val channel: TrezorRawUsbChannel,
    private val promptPolicy: TrezorPromptPolicy = TrezorPromptPolicy.Interactive,
) : ExternalSignerChannel {
    private val decoder = FramingDecoder()

    override fun writePacket(packet: ByteArray) =
        mapFailure { channel.writePacket(packet) }

    override fun readPacket(deadlineNanos: Long): ByteArray =
        mapFailure { channel.readPacket(deadlineNanos) }.also(::rejectPromptIfUnattended)

    override fun cancel() =
        mapFailure(channel::cancel)

    // Native code acks device prompts itself, so an unattended session must refuse them here.
    private fun rejectPromptIfUnattended(packet: ByteArray) {
        if (promptPolicy == TrezorPromptPolicy.Interactive) return
        // A malformed packet leaves the decoder state intact; the native side rejects it on its own.
        val message = tryOrNull { decoder.offer(packet) } ?: return
        if (message.msgType !in DEVICE_PROMPTS) return
        val failure = ExternalSignerException(
            HardwareWalletErrorCode.PendingDeviceVerification,
            "Trezor requires an action on the device",
        )
        cancelPrompt(failure)
        throw failure
    }

    // The device answers Cancel with a Failure; drain it so the next session starts clean.
    private fun cancelPrompt(failure: Exception) {
        try {
            FramingCodec.encode(MessageType.MessageType_Cancel_VALUE, Cancel.getDefaultInstance().toByteArray())
                .forEach(channel::writePacket)
            val deadline = System.nanoTime() + CANCEL_DRAIN_TIMEOUT_NANOS
            val drain = FramingDecoder()
            do {
                val message = drain.offer(channel.readPacket(deadline))
            } while (message == null)
        } catch (error: Exception) {
            failure.addSuppressed(error)
        }
    }

    private fun <T> mapFailure(block: () -> T): T =
        try {
            block()
        } catch (error: ExternalSignerException) {
            throw error
        } catch (error: Exception) {
            throw ExternalSignerException(
                error.hardwareWalletErrorCode(),
                "Trezor channel operation failed",
                error,
            )
        }

    private companion object {
        val DEVICE_PROMPTS = setOf(
            MessageType.MessageType_ButtonRequest_VALUE,
            MessageType.MessageType_PinMatrixRequest_VALUE,
            MessageType.MessageType_PassphraseRequest_VALUE,
        )
        val CANCEL_DRAIN_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5)
    }
}
