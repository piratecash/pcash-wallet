package cash.p.terminal.modules.blockchainsettings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.blockchain_status
import cash.p.terminal.resources.zcash_network_auto_short
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.imageUrl
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow

class BlockchainSettingsViewModel(
    private val service: BlockchainSettingsService
) : ViewModel() {

    var btcLikeChains by mutableStateOf<List<BlockchainSettingsModule.BlockchainViewItem>>(listOf())
        private set

    var otherChains by mutableStateOf<List<BlockchainSettingsModule.BlockchainViewItem>>(listOf())
        private set

    var statusOnlyChains by mutableStateOf<List<BlockchainSettingsModule.BlockchainViewItem>>(listOf())
        private set

    init {
        viewModelScope.launch {
            service.blockchainItemsObservable.asFlow().collect {
                sync(it)
            }
        }

        service.start()
        sync(service.blockchainItems)
    }

    override fun onCleared() {
        service.stop()
    }

    private fun sync(blockchainItems: List<BlockchainSettingsModule.BlockchainItem>) {
        viewModelScope.launch {
            btcLikeChains = blockchainItems
                .filterIsInstance<BlockchainSettingsModule.BlockchainItem.Btc>()
                .map { item ->
                    BlockchainSettingsModule.BlockchainViewItem(
                        title = item.blockchain.name,
                        subtitle = Translator.getString(item.restoreMode.getTitle(item.blockchain.type)),
                        imageUrl = item.blockchain.type.imageUrl,
                        blockchainItem = item
                    )
                }

            otherChains = blockchainItems
                .mapNotNull { item ->
                    when (item) {
                        is BlockchainSettingsModule.BlockchainItem.Evm -> BlockchainSettingsModule.BlockchainViewItem(
                            title = item.blockchain.name,
                            subtitle = item.syncSource.name,
                            imageUrl = item.blockchain.type.imageUrl,
                            blockchainItem = item
                        )

                        is BlockchainSettingsModule.BlockchainItem.Solana ->
                            BlockchainSettingsModule.BlockchainViewItem(
                            title = item.blockchain.name,
                            subtitle = item.rpcSource.name,
                            imageUrl = item.blockchain.type.imageUrl,
                            blockchainItem = item
                        )

                        is BlockchainSettingsModule.BlockchainItem.Zcash ->
                            BlockchainSettingsModule.BlockchainViewItem(
                                title = item.blockchain.name,
                                subtitle = if (item.auto) {
                                    Translator.getString(Res.string.zcash_network_auto_short, item.server.name)
                                } else {
                                    item.server.name
                                },
                                imageUrl = item.blockchain.type.imageUrl,
                                blockchainItem = item
                            )

                        else -> null
                    }
                }

            statusOnlyChains = blockchainItems
                .filterIsInstance<BlockchainSettingsModule.BlockchainItem.StatusOnly>()
                .map { item ->
                    BlockchainSettingsModule.BlockchainViewItem(
                        title = item.blockchain.name,
                        subtitle = Translator.getString(Res.string.blockchain_status),
                        imageUrl = item.blockchain.type.imageUrl,
                        blockchainItem = item
                    )
                }
        }
    }

}
