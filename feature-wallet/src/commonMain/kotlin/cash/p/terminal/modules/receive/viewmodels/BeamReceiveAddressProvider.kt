package cash.p.terminal.modules.receive.viewmodels

import cash.p.beam.BeamAddress
import cash.p.beam.BeamAddressType
import cash.p.terminal.core.managers.BeamNetwork
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.withContext

internal class BeamReceiveAddress(val address: BeamAddress, val isCurrent: () -> Boolean)

internal class BeamReceivePendingException : IllegalStateException("BEAM receive initialization pending")

internal interface BeamReceiveAddressProvider {
    fun changes(account: Account): Flow<Unit>
    suspend fun receiveAddress(account: Account, type: BeamAddressType): BeamReceiveAddress
}

internal class SessionBeamReceiveAddressProvider(
    private val owner: BeamSessionOwner,
    private val accountManager: IAccountManager,
    private val adapterManager: IAdapterManager,
    private val dispatcherProvider: DispatcherProvider,
    private val network: BeamNetwork = BeamNetwork.Mainnet,
) : BeamReceiveAddressProvider {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun changes(account: Account): Flow<Unit> = merge(
        accountManager.activeAccountStateFlow,
        adapterManager.adaptersReadyObservable.asFlow(),
        adapterManager.initializationInProgressFlow,
    ).map { matchingSession(account) to adapterManager.initializationInProgressFlow.value }
        .distinctUntilChanged()
        .flatMapLatest { (session, initializing) ->
            // Retirement closes the SDK before AdapterManager publishes its replacement.
            (session?.wallet?.state ?: flowOf(null)).map { matchingSession(account) to initializing }
        }
        .distinctUntilChanged()
        .map { Unit }

    override suspend fun receiveAddress(account: Account, type: BeamAddressType): BeamReceiveAddress =
        withContext(dispatcherProvider.io) {
            check(accountManager.activeAccount?.id == account.id) { "BEAM receive account is inactive" }
            // AdapterManager owns acquisition and retirement; receive only borrows its current session.
            val session = matchingSession(account) ?: run {
                if (adapterManager.initializationInProgressFlow.value) throw BeamReceivePendingException()
                error("BEAM receive session is unavailable")
            }
            val address = owner.withSession(session) {
                check(matchingSession(account) === session) { "BEAM receive session changed" }
                it.receiveAddress(type)
            }
            check(matchingSession(account) === session) { "BEAM receive session changed" }
            BeamReceiveAddress(address) { matchingSession(account) === session }
        }

    private fun matchingSession(account: Account) = owner.current?.takeIf {
        it.accountId == account.id && it.network == network && accountManager.activeAccount?.id == account.id
    }
}
