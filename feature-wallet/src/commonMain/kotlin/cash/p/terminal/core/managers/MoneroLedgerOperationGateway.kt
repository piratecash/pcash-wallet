package cash.p.terminal.core.managers

import cash.p.terminal.ledger.domain.LedgerAppAccess
import cash.p.terminal.ledger.domain.LedgerConnector
import cash.p.terminal.ledger.domain.LedgerRejectedException
import cash.p.terminal.ledger.domain.LedgerUserOperations
import cash.p.terminal.ledger.domain.LedgerWaitNotice
import cash.p.terminal.ledger.domain.LedgerWaitReason
import cash.p.terminal.ledger.monero.LEDGER_MONERO_APP
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.apdu.StatusWord
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.monero
import com.piratecash.monero.signer.ExternalSigner
import com.piratecash.monero.signer.ExternalSignerApduChannel
import com.piratecash.monero.signer.ExternalSignerException
import com.piratecash.monero.signer.HardwareWalletErrorCode
import com.piratecash.monero.signer.HardwareWalletOperationException
import io.horizontalsystems.core.IBackgroundManager
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** Runs a Monero wallet2 operation with the open Ledger Monero app as the wallet's device. */
class MoneroLedgerOperationGateway(
    private val appAccess: LedgerAppAccess,
    private val backgroundManager: IBackgroundManager,
    private val dispatcherProvider: DispatcherProvider,
    private val userOperations: LedgerUserOperations,
    private val connector: LedgerConnector,
) : MoneroHardwareGateway {

    override suspend fun <T> execute(account: Account, block: (String) -> T): T =
        execute(account, admit = {}, switchApp = true, waitReason = null, block)

    // Sessions are exclusive, so the running one's channel is the only one wallet2 can be using.
    @Volatile
    private var sessionChannel: LedgerApduChannel? = null

    val isDevicePresent: Boolean get() = connector.isDevicePresent

    /** False once the running session's device failed, even when wallet2 swallowed the failure. */
    val isSessionIntact: Boolean
        get() = sessionChannel?.transportFailure == null

    /** Throws the running session's device failure, even one wallet2 swallowed. */
    fun requireIntactSession() {
        sessionChannel?.requireIntact()
    }

    override suspend fun <T> executeUnattended(account: Account, block: (String) -> T): T =
        executeUnattended(account, yielding = false, block)

    override suspend fun <T> executeYielding(account: Account, block: (String) -> T): T =
        executeUnattended(account, yielding = true, block)

    private suspend fun <T> executeUnattended(
        account: Account,
        yielding: Boolean,
        block: (String) -> T,
    ): T {
        val switchApp = currentCoroutineContext()[LedgerAppSwitchForbidden.Key] == null
        val admit = if (yielding) ::requireForegroundAndIdleUser else ::requireForeground
        val waitReason = if (yielding) LedgerWaitReason.Sync else LedgerWaitReason.DeviceRequest
        // A sync's session must never be torn mid-batch by its caller's cancellation; queueing for it still may.
        val sessionContext = if (yielding) NonCancellable else EmptyCoroutineContext
        return try {
            execute(account, admit, switchApp, waitReason, block, sessionContext)
        } catch (error: LedgerException.WrongAppOpen) {
            if (switchApp) throw error
            throw LedgerSessionDeferredException()
        }
    }

    /** The periodic check for an open Monero app: it never switches apps and never reaches a backgrounded device. */
    suspend fun isMoneroAppOpen(): Boolean = appAccess.isOpen(LEDGER_MONERO_APP, ::requireForeground)

    // Checked once the device is held: the device may prompt, so unasked work waits until the user can see it.
    private fun requireForeground() {
        if (!backgroundManager.inForeground) throw LedgerSessionDeferredException()
    }

    private fun requireForegroundAndIdleUser() {
        requireForeground()
        if (userOperations.isActive) throw LedgerSessionDeferredException()
    }

    private suspend fun <T> execute(
        account: Account,
        admit: () -> Unit,
        switchApp: Boolean,
        waitReason: LedgerWaitReason?,
        block: (String) -> T,
        sessionContext: CoroutineContext = EmptyCoroutineContext,
    ): T {
        val walletPublicKey = requireNotNull(account.type as? AccountType.LedgerDevice).walletPublicKey
        val runBlock: suspend () -> T = { runInterruptible(dispatcherProvider.io) { block(walletPublicKey) } }
        return appAccess.withLedgerApp(account, TokenQuery.monero, admit, switchApp) { transport ->
            withContext(sessionContext) { runSession(transport, waitReason, runBlock) }
        }
    }

    private suspend fun <T> runSession(
        transport: LedgerTransport,
        waitReason: LedgerWaitReason?,
        runBlock: suspend () -> T,
    ): T {
        val channel = LedgerApduChannel(transport)
        sessionChannel = channel
        try {
            return withExternalSignerRegistration(ExternalSigner.registerApdu(channel)) {
                if (waitReason == null) runBlock() else remindingWaitingUsers(waitReason, runBlock)
            }.also { channel.requireIntact() }
        } catch (error: Throwable) {
            throw channel.explain(error)
        } finally {
            sessionChannel = null
        }
    }

    // Unasked work holds the device until a safe point or the user's confirmation, so a waiting user is told why.
    // A child of the session's job rather than a nested scope, so the block's exception reaches explain() as thrown.
    private suspend fun <T> remindingWaitingUsers(reason: LedgerWaitReason, block: suspend () -> T): T {
        val reminder = CoroutineScope(currentCoroutineContext()).launch {
            userOperations.remindWhileUsersWait(LedgerWaitNotice(LEDGER_MONERO_APP, reason))
        }
        try {
            return block()
        } finally {
            reminder.cancel()
        }
    }
}

/** Marks unattended work that runs only if the Monero app is already open: it never switches the device's app. */
object LedgerAppSwitchForbidden : AbstractCoroutineContextElement(LedgerAppSwitchForbidden.Key) {
    object Key : CoroutineContext.Key<LedgerAppSwitchForbidden>
}

/** A Ledger session put off before reaching the device (backgrounded, other app open); a later trigger retries it. */
class LedgerSessionDeferredException : IllegalStateException("Ledger is used only while the app is in the foreground")

/** A background Ledger session stopped mid-way so a user operation could take the device; a later trigger resumes it. */
class LedgerSessionYieldedException : IllegalStateException("Ledger was given up to a user operation")

/** wallet2 calls [exchange] on its own threads; the channel remembers why the device stopped answering. */
internal class LedgerApduChannel(private val transport: LedgerTransport) : ExternalSignerApduChannel {
    private val exchanges = SupervisorJob()
    private val mutex = Mutex()

    @Volatile
    var transportFailure: Throwable? = null
        private set

    @Volatile
    private var deniedOnDevice = false

    override fun exchange(apdu: ByteArray): ByteArray {
        val response = try {
            runBlocking(exchanges) { mutex.withLock { transport.exchange(apdu) } }
        } catch (error: CancellationException) {
            throw exchangeFailed(error)
        } catch (error: Exception) {
            transportFailure = error
            throw exchangeFailed(error)
        }
        // The Monero app answers a refusal on its screen with this status word.
        if (response.statusWord() == StatusWord.SECURITY_STATUS_NOT_SATISFIED) deniedOnDevice = true
        return response
    }

    override fun cancel() {
        exchanges.cancel()
    }

    fun requireIntact() {
        transportFailure?.let { throw it }
    }

    /**
     * wallet2 reports device trouble as a generic failure, so the channel's view decides what happened.
     * A failure wallet2 swallowed before a yield is still a device failure.
     */
    fun explain(error: Throwable): Throwable = when {
        error is CancellationException -> error
        transportFailure != null -> checkNotNull(transportFailure)
        error is LedgerSessionYieldedException -> error
        error is ExternalSignerException -> HardwareWalletOperationException(error.hardwareErrorCode, error.message)
            .also { it.initCause(error) }
        deniedOnDevice && error !is HardwareWalletOperationException -> LedgerRejectedException()
        else -> error
    }

    private fun exchangeFailed(cause: Exception) =
        ExternalSignerException(HardwareWalletErrorCode.Disconnected, "Ledger exchange failed", cause)

    private fun ByteArray.statusWord(): Int =
        if (size < 2) -1 else (this[size - 2].toInt() and 0xFF shl 8) or (this[size - 1].toInt() and 0xFF)
}
