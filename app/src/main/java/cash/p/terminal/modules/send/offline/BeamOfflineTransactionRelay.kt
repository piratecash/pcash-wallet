package cash.p.terminal.modules.send.offline

import cash.p.beam.BeamInspectedTransaction
import cash.p.beam.BeamNetwork
import cash.p.beam.BeamRelayConfig
import cash.p.beam.BeamRelayResult
import cash.p.beam.BeamTransactionInspector
import cash.p.beam.BeamTransactionRelay
import cash.p.beam.BeamTransactionRules
import cash.p.terminal.core.hexToByteArray
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.entities.DecodedOfflineTransaction
import cash.p.terminal.wallet.IAccountManager
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

class BeamOfflineTransactionRelay(
    private val dispatcherProvider: DispatcherProvider,
    private val offlineModeManager: OfflineModeManager,
    private val accountManager: IAccountManager,
) {
    class Prepared internal constructor(
        private val bytes: ByteArray,
        val transaction: BeamInspectedTransaction,
    ) {
        internal fun copyBytes() = bytes.copyOf()
    }

    class Offline(val acceptanceUnknown: Boolean) : Exception()

    suspend fun prepare(
        rawHex: String,
        network: BeamNetwork,
        envelope: DecodedOfflineTransaction?,
    ): Prepared = withContext(dispatcherProvider.io) {
        require(network == BeamNetwork.Mainnet)
        require(rawHex.length in 2..BeamTransactionInspector.MAX_TRANSACTION_BYTES * 2)
        require(rawHex.length % 2 == 0 && rawHex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
        val rules = BeamTransactionInspector.supportedRules(network)
        require(rules.network == network)
        envelope?.let { validateEnvelope(it, rawHex, rules) }
        val bytes = rawHex.hexToByteArray()
        val inspected = BeamTransactionInspector.inspect(bytes, rules)
        require(inspected.rules == rules && inspected.serializedSize == bytes.size)
        envelope?.let {
            require(inspected.mainKernelId == it.beamMetadata?.mainKernelId && inspected.mainKernelId == it.txHash)
        }
        Prepared(bytes, inspected)
    }

    private fun validateEnvelope(envelope: DecodedOfflineTransaction, rawHex: String, rules: BeamTransactionRules) {
        val metadata = requireNotNull(envelope.beamMetadata)
        require(envelope.blockchainUid == BlockchainType.Beam.uid && envelope.rawHex == rawHex)
        require(envelope.token.tokenQueryId == "beam|native" && envelope.token.decimals == 8)
        require(envelope.token.coinUid == "beam")
        require(envelope.fee?.let { it.tokenQueryId == "beam|native" && it.decimals == 8 } != false)
        require(metadata.version == 1 && metadata.network == "mainnet" && metadata.rulesSignature == rules.signature)
        require(metadata.mainKernelId == envelope.txHash)
        // Amount, receiver and time are confidential/untrusted; they are never promoted to wallet history.
    }

    suspend fun relay(prepared: Prepared): BeamRelayResult {
        val accountId = accountManager.activeAccount?.id
        return try {
            withContext(dispatcherProvider.io) { relayOnline(prepared, accountId) }
        } finally {
            requireSameAccount(accountId)
        }
    }

    private suspend fun relayOnline(prepared: Prepared, accountId: String?): BeamRelayResult = coroutineScope {
        requireOnline(accountId)
        val blocked = async { awaitBlocked(accountId) }
        val attempt = async {
            requireOnline(accountId)
            BeamTransactionRelay.relay(prepared.copyBytes(), BeamRelayConfig(prepared.transaction.rules))
        }
        try {
            select {
                blocked.onAwait {
                    attempt.cancelAndJoin()
                    requireSameAccount(accountId)
                    throw Offline(acceptanceUnknown = true)
                }
                attempt.onAwait {
                    requireSameAccount(accountId)
                    it
                }
            }
        } finally {
            blocked.cancel()
        }
    }

    private suspend fun awaitBlocked(accountId: String?) = combine(
        offlineModeManager.stateFlow,
        offlineModeManager.effectiveFlow,
        accountManager.activeAccountStateFlow.map { Unit }.onStart { emit(Unit) },
    ) { _, paused, _ ->
        accountManager.activeAccount?.id != accountId || isOffline(accountId) ||
            accountId?.let { OfflineKey(it, BlockchainType.Beam) in paused } == true
    }.first { it }

    private suspend fun requireOnline(accountId: String?) {
        currentCoroutineContext().ensureActive()
        requireSameAccount(accountId)
        if (isOffline(accountId)) throw Offline(acceptanceUnknown = false)
    }

    private fun requireSameAccount(accountId: String?) {
        if (accountManager.activeAccount?.id != accountId) throw CancellationException("Relay account context changed")
    }

    private fun isOffline(accountId: String?): Boolean {
        val key = accountId?.let { OfflineKey(it, BlockchainType.Beam) } ?: return false
        return offlineModeManager.stateFlow.value[key]?.offline == true || offlineModeManager.isNetworkPaused(key)
    }
}
