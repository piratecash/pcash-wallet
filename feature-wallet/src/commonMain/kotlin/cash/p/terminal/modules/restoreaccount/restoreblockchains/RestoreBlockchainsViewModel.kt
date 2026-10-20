package cash.p.terminal.modules.restoreaccount.restoreblockchains

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_platform_placeholder_32
import cash.p.terminal.wallet.Clearable
import cash.p.terminal.core.description
import cash.p.terminal.ui_compose.components.ImageSource
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.imageUrl
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow

class RestoreBlockchainsViewModel(
    private val service: RestoreBlockchainsService,
    private val clearables: List<Clearable>
) : ViewModel() {

    var viewItems by mutableStateOf<List<CoinViewItem<Blockchain>>?>(null)
        private set
    var restored by mutableStateOf(false)
        private set
    var restoreEnabled by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            service.itemsObservable.asFlow().collect {
                sync(it)
            }
        }

        viewModelScope.launch {
            service.canRestore.asFlow().collect {
                restoreEnabled = it
            }
        }

        sync(service.items)
    }

    private fun sync(items: List<RestoreBlockchainsService.Item>) {
        viewItems = items.map { viewItem(it) }
    }

    private fun viewItem(
        item: RestoreBlockchainsService.Item,
    ) = CoinViewItem(
        item = item.blockchain,
        imageSource = ImageSource.Remote(item.blockchain.type.imageUrl, Res.drawable.ic_platform_placeholder_32),
        title = item.blockchain.name,
        subtitle = item.blockchain.description,
        enabled = item.enabled,
        hasSettings = item.hasSettings,
        hasInfo = false
    )

    fun enable(blockchain: Blockchain) {
        service.enable(blockchain)
    }

    fun disable(blockchain: Blockchain) {
        service.disable(blockchain)
    }

    /***
     * Called after user chosen different types of one token (e.g. transparent/shielded/universla for Zcash)
     */
    fun showApproveSettings(token: Token) {
        service.showApproveSettings(token)
    }

    fun onClickSettings(blockchain: Blockchain) {
        service.configure(blockchain)
    }

    fun onRestore() {
        service.restore()
        restored = true
    }

    override fun onCleared() {
        clearables.forEach(Clearable::clear)
    }
}
