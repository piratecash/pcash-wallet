package cash.p.terminal.core.managers

import cash.p.terminal.trezor.client.TrezorPublicKeySpecs
import cash.p.terminal.trezorkit.client.TrezorFeatures
import cash.p.terminal.trezorkit.client.TrezorSessionId
import cash.p.terminal.trezorkit.client.UsbTrezorClient
import cash.p.terminal.trezorkit.protocol.MessageTypeRegistry
import cash.p.terminal.trezorkit.protocol.TrezorSession
import cash.p.terminal.trezorkit.transport.ITrezorTransport
import cash.p.terminal.trezorkit.transport.TrezorRawUsbChannel
import cash.p.terminal.trezorkit.transport.TrezorUsbCoordinator
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import com.piratecash.monero.signer.ExternalSigner
import com.piratecash.monero.signer.ExternalSignerChannel
import com.piratecash.monero.signer.ExternalSignerException
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible

/**
 * The only app-layer bridge that knows both the Trezor and Monero kit contracts.
 */
class MoneroTrezorOperationGateway internal constructor(
    private val coordinator: TrezorUsbCoordinator,
    private val readiness: MoneroTrezorReadiness,
    private val dispatcherProvider: DispatcherProvider,
) : MoneroHardwareGateway {
    suspend fun <T> execute(block: () -> T): T =
        executeInternal(null) { block() }

    override suspend fun <T> execute(
        account: Account,
        block: (String) -> T,
    ): T = executeInternal(account, block)

    private suspend fun <T> executeInternal(
        account: Account?,
        block: (String) -> T,
    ): T {
        try {
            return coordinator.withRawChannel { channel ->
                val device = verifyDevice(channel, account)
                val registration = try {
                    ExternalSigner.register(
                        TrezorExternalSignerChannel(channel),
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
            throw mapFailure(error)
        }
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
    ): VerifiedDevice {
        val accountType = account?.requireTrezorType()
        val read = readDevice(channel, deriveWalletIdentity = accountType != null)
        try {
            readiness.requireLive(read.features, accountType)
            if (account != null) {
                readiness.requireWallet(account, read.walletPublicKey)
            }
            return VerifiedDevice(
                walletPublicKey = read.walletPublicKey,
                sessionId = read.sessionId.toByteArray(),
            )
        } finally {
            read.sessionId.close()
        }
    }

    private suspend fun readDevice(
        channel: TrezorRawUsbChannel,
        deriveWalletIdentity: Boolean,
    ): DeviceRead {
        val transport = BorrowedRawChannelTransport(channel, dispatcherProvider.io)
        val session = TrezorSession(transport, MessageTypeRegistry, dispatcherProvider.io)
        return UsbTrezorClient(session).connect {
            var features = getFeatures()
            try {
                val walletPublicKey = if (deriveWalletIdentity) {
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
    }

    private fun Account.requireTrezorType(): AccountType.TrezorDevice =
        type as? AccountType.TrezorDevice
            ?: throw HardwareWalletOperationException(
                HardwareWalletErrorCode.ProvisioningTargetMismatch,
                "Monero device wallet requires a Trezor account",
            )

    private data class VerifiedDevice(
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

internal class TrezorExternalSignerChannel(
    private val channel: TrezorRawUsbChannel,
) : ExternalSignerChannel {
    override fun writePacket(packet: ByteArray) =
        mapFailure { channel.writePacket(packet) }

    override fun readPacket(deadlineNanos: Long): ByteArray =
        mapFailure { channel.readPacket(deadlineNanos) }

    override fun cancel() =
        mapFailure(channel::cancel)

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
}
