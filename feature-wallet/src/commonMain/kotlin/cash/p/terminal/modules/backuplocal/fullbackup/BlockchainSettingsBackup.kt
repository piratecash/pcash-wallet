package cash.p.terminal.modules.backuplocal.fullbackup

import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.EvmBlockchains
import cash.p.terminal.core.managers.EvmSyncSourceManager
import cash.p.terminal.core.managers.SolanaRpcSourceManager
import cash.p.terminal.core.storage.BlockchainSettingsStorage
import cash.p.terminal.core.storage.EvmSyncSourceStorage
import cash.p.terminal.entities.BtcRestoreMode
import cash.p.terminal.entities.TransactionDataSortMode
import io.horizontalsystems.core.entities.BlockchainType

/** The sync-source part of the settings backup, the same on every platform. */
class BlockchainSettingsBackup(
    private val btcBlockchainManager: BtcBlockchainManager,
    private val evmBlockchains: EvmBlockchains,
    private val evmSyncSourceManager: EvmSyncSourceManager,
    private val evmSyncSourceStorage: EvmSyncSourceStorage,
    private val blockchainSettingsStorage: BlockchainSettingsStorage,
    private val solanaRpcSourceManager: SolanaRpcSourceManager,
) {

    fun btcModes(): List<BtcMode> = btcBlockchainManager.allBlockchains.map { blockchain ->
        val restoreMode = btcBlockchainManager.restoreMode(blockchain.type)
        val sortMode = btcBlockchainManager.transactionSortMode(blockchain.type)
        BtcMode(blockchain.uid, restoreMode.raw, sortMode.raw)
    }

    fun evmSyncSources(cachedKey: ByteArray, passphrase: String): EvmSyncSources {
        val selected = evmBlockchains.allBlockchains.map { blockchain ->
            val syncSource = evmSyncSourceManager.getSyncSource(blockchain.type)
            EvmSyncSourceBackup(blockchain.uid, syncSource.uri.toString(), null)
        }

        val custom = evmBlockchains.allBlockchains.flatMap { blockchain ->
            evmSyncSourceManager.customSyncSources(blockchain.type).map { syncSource ->
                val auth = syncSource.auth?.let {
                    encryptedBackupCrypto(it.toByteArray(Charsets.UTF_8), cachedKey, passphrase)
                }
                EvmSyncSourceBackup(blockchain.uid, syncSource.uri.toString(), auth)
            }
        }

        return EvmSyncSources(selected = selected, custom = custom)
    }

    fun solanaSyncSource() = SolanaSyncSource(BlockchainType.Solana.uid, solanaRpcSourceManager.rpcSource.name)

    fun customRpcCount(): Int? = evmSyncSourceStorage.getAll().ifEmpty { null }?.size

    fun restore(settings: Settings, passphrase: String) {
        settings.btcModes.forEach { btcMode ->
            val blockchainType = BlockchainType.fromUid(btcMode.blockchainTypeId)

            val restoreMode = BtcRestoreMode.entries.firstOrNull { it.raw == btcMode.restoreMode }
            restoreMode?.let { btcBlockchainManager.save(it, blockchainType) }

            val sortMode = TransactionDataSortMode.entries.firstOrNull { it.raw == btcMode.sortMode }
            sortMode?.let { btcBlockchainManager.save(it, blockchainType) }
        }

        settings.evmSyncSources.custom.forEach { syncSource ->
            val blockchainType = BlockchainType.fromUid(syncSource.blockchainTypeId)
            val auth = syncSource.auth?.let {
                String(decryptedBackupCrypto(it, passphrase), Charsets.UTF_8)
            }
            evmSyncSourceManager.saveSyncSource(blockchainType, syncSource.url, auth)
        }

        settings.evmSyncSources.selected.forEach { syncSource ->
            val blockchainType = BlockchainType.fromUid(syncSource.blockchainTypeId)
            blockchainSettingsStorage.save(syncSource.url, blockchainType)
        }

        settings.solanaSyncSource?.let { solanaSyncSource ->
            blockchainSettingsStorage.save(solanaSyncSource.name, BlockchainType.Solana)
        }
    }
}
