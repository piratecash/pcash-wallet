package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.UsedAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf


interface IReceiveAdapter {
    val receiveAddress: String
    val isMainNet: Boolean

    suspend fun isAddressActive(address: String): Boolean {
        return true
    }

    fun usedAddresses(change: Boolean): List<UsedAddress> {
        return listOf()
    }

    val isAddressHistorySupported: Boolean get() = false

    /** The next address the adapter can prove is unused, or [receiveAddress] when it cannot. */
    suspend fun freshReceiveAddress(): String = receiveAddress

    /** Emits once on subscription and again whenever a later [freshReceiveAddress] call would answer differently. */
    val freshReceiveAddressChanges: Flow<Unit> get() = flowOf(Unit)
}

interface OneTimeReceiveAdapter {
    suspend fun generateOneTimeAddress(): String?
}
