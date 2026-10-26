package cash.p.terminal.modules.contacts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import io.horizontalsystems.core.entities.BlockchainType

class ChooseContactViewModel(
    private val repository: ContactsRepository,
    private val blockchainType: BlockchainType
) : ViewModel() {

    var items: List<ContactViewItem> by mutableStateOf(listOf())
        private set

    private var query: String? = null

    init {
        rebuildItems()
    }

    fun onEnterQuery(query: String?) {
        this.query = query

        rebuildItems()
    }

    private fun rebuildItems() {
        items = repository.getContactsFiltered(blockchainType, query)
            .map {
                ContactViewItem(
                    it.name,
                    it.addresses.first { it.blockchain.type == blockchainType }.address
                )
            }
    }

}

data class ContactViewItem(val name: String, val address: String)
