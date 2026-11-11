package cash.p.terminal.core.storage

import cash.p.terminal.entities.EvmSyncSourceRecord
import io.horizontalsystems.core.entities.BlockchainType

class EvmSyncSourceStorage(private val router: StorageRouter) {

    private val dao: EvmSyncSourceDao
        get() = router.shared.evmSyncSourceDao()

    fun evmSyncSources(blockchainType: BlockchainType): List<EvmSyncSourceRecord> {
        return dao.getEvmSyncSources(blockchainType.uid)
    }

    fun getAll() = dao.getAll()

    fun save(evmSyncSourceRecord: EvmSyncSourceRecord) {
        dao.insert(evmSyncSourceRecord)
    }

    fun delete(blockchainTypeUid: String, url: String) {
        dao.delete(blockchainTypeUid, url)
    }

}
