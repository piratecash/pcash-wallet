package cash.p.terminal.ledger.ethereum

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.horizontalsystems.ethereumkit.core.hexStringToByteArray
import io.horizontalsystems.ethereumkit.core.toRawHexString
import kotlinx.coroutines.flow.first

/** CAL-signed descriptors and certificates; offline signing reads only this, never the network. */
class LedgerTokenDescriptorStore(private val dataStore: DataStore<Preferences>) {

    suspend fun descriptor(chainId: Int, contract: String): ByteArray? =
        read(erc20Key(chainId, contract))?.hexStringToByteArray()

    suspend fun save(chainId: Int, contract: String, descriptor: ByteArray) {
        write(erc20Key(chainId, contract), descriptor.toRawHexString())
    }

    suspend fun signed(key: String): SignedDescriptor? = read(key)?.split(SEPARATOR)?.let { (data, signature) ->
        SignedDescriptor(data.hexStringToByteArray(), signature.hexStringToByteArray())
    }

    suspend fun save(key: String, descriptor: SignedDescriptor) {
        write(key, descriptor.data.toRawHexString() + SEPARATOR + descriptor.signature.toRawHexString())
    }

    private suspend fun read(key: String): String? = dataStore.data.first()[stringPreferencesKey(key)]

    private suspend fun write(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    private fun erc20Key(chainId: Int, contract: String) = "$chainId:${contract.lowercase()}"

    private companion object {
        const val SEPARATOR = ":"
    }
}
