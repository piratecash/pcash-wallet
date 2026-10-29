package cash.p.terminal.modules.enablecoin.blockchaintokens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.description
import cash.p.terminal.core.title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_platform_placeholder_32
import io.horizontalsystems.core.Translator
import cash.p.terminal.ui_compose.components.ImageSource
import cash.p.terminal.ui.extensions.BottomSheetSelectorMultipleSheet
import cash.p.terminal.ui.extensions.BottomSheetSelectorViewItem
import cash.p.terminal.wallet.title
import io.horizontalsystems.core.imageUrl
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import cash.p.terminal.resources.AddressFormatSettings_Description

class BlockchainTokensViewModel(
    private val service: BlockchainTokensService
) : ViewModel() {

    var showBottomSheetDialog by mutableStateOf(false)
        private set

    var config: BottomSheetSelectorMultipleSheet.Config? = null
        private set
    var currentRequest: BlockchainTokensService.Request? = null
        private set

    init {
        viewModelScope.launch {
            service.requestObservable.asFlow().collect {
                handle(it)
            }
        }
    }

    private fun handle(request: BlockchainTokensService.Request) {
        currentRequest = request
        val blockchain = request.blockchain
        val selectedTokenIndexes = request.enabledTokens.map { request.tokens.indexOf(it) }

        val config = BottomSheetSelectorMultipleSheet.Config(
            icon = ImageSource.Remote(blockchain.type.imageUrl, Res.drawable.ic_platform_placeholder_32),
            title = blockchain.name,
            description = Translator.getString(Res.string.AddressFormatSettings_Description),
            selectedIndexes = selectedTokenIndexes,
            allowEmpty = request.allowEmpty,
            viewItems = request.tokens.map { token ->
                BottomSheetSelectorViewItem(
                    title = token.title.ifEmpty { token.coin.name },
                    subtitle = token.description.ifEmpty { token.blockchainType.title },
                )
            }
        )
        showBottomSheetDialog = true
        this.config = config
    }

    fun bottomSheetDialogShown() {
        showBottomSheetDialog = false
    }

    fun onSelect(indexes: List<Int>) {
        currentRequest?.let { currentRequest ->
            service.select(indexes.map { currentRequest.tokens[it] }, currentRequest.blockchain)
        }
    }

    fun onCancelSelect() {
        currentRequest?.let { currentRequest ->
            service.cancel(currentRequest.blockchain)
        }
    }

}
