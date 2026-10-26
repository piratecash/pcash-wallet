package cash.p.terminal.modules.contacts

import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.common.CommonParcelize

sealed class Mode : CommonParcelable {
    @CommonParcelize
    object Full : Mode()

    @CommonParcelize
    class AddAddressToExistingContact(val blockchainType: BlockchainType, val address: String) : Mode()

    @CommonParcelize
    class AddAddressToNewContact(val blockchainType: BlockchainType, val address: String) : Mode()
}
