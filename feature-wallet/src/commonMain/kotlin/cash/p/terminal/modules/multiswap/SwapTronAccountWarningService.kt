package cash.p.terminal.modules.multiswap

import cash.p.terminal.core.ServiceState
import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.core.adapters.toReceiveAccountStatus
import cash.p.terminal.core.managers.TronKitManager
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.ReceiveAccountStatus
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tells whether the swap would deliver funds to a TRON account that other keys control. */
class SwapTronAccountWarningService(
    private val adapterManager: IAdapterManager,
    private val accountManager: IAccountManager,
    private val tronKitManager: TronKitManager,
    private val dispatcherProvider: DispatcherProvider,
) : ServiceState<Boolean>() {

    private lateinit var coroutineScope: CoroutineScope
    private var hasTarget = false
    private var job: Job? = null
    private var controlledByOthers = false

    override fun createState() = controlledByOthers

    fun start(coroutineScope: CoroutineScope) {
        this.coroutineScope = coroutineScope
    }

    fun setTarget(tokenOut: Token?, customRecipient: Boolean) {
        val target = tokenOut?.takeIf { it.blockchainType == BlockchainType.Tron && !customRecipient }
        // All TRON tokens belong to one account, so switching between them needs no new request.
        if ((target != null) == hasTarget) return

        hasTarget = target != null
        job?.cancel()
        publish(false)
        job = target?.let { token ->
            coroutineScope.launch {
                // The kit's request blocks its thread. Returning to a cancelled job throws: no stale result.
                val controlled = withContext(dispatcherProvider.io) { isControlledByOthers(token) }
                // A failed check is not an answer: the next setTarget retries.
                hasTarget = controlled != null
                publish(controlled == true)
            }
        }
    }

    private fun publish(value: Boolean) {
        controlledByOthers = value
        emitState()
    }

    private suspend fun isControlledByOthers(token: Token): Boolean? =
        tryOrNull { accountStatus(token) == ReceiveAccountStatus.ControlledByOthers }

    private suspend fun accountStatus(token: Token): ReceiveAccountStatus {
        adapterManager.getAdapterForToken<IReceiveAdapter>(token)?.let {
            return it.accountStatus(it.receiveAddress)
        }
        val account = accountManager.activeAccount ?: return ReceiveAccountStatus.Active
        val wrapper = try {
            tronKitManager.getTronKitWrapper(account)
        } catch (_: UnsupportedAccountException) {
            return ReceiveAccountStatus.Active
        }
        try {
            val tronKit = wrapper.tronKit
            return tronKit.accountStatus(tronKit.address).toReceiveAccountStatus(hasSigner = wrapper.signer != null)
        } finally {
            withContext(NonCancellable) { tronKitManager.unlink(account) }
        }
    }
}
