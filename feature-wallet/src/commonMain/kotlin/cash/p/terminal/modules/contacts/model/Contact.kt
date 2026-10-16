package cash.p.terminal.modules.contacts.model

import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import io.horizontalsystems.core.entities.Blockchain
import java.util.*

@CommonParcelize
data class Contact(
    val uid: String,
    val name: String,
    val addresses: List<ContactAddress>
) : CommonParcelable

@CommonParcelize
data class ContactAddress(
    val blockchain: Blockchain,
    val address: String
) : CommonParcelable {
    override fun equals(other: Any?): Boolean {
        return other is ContactAddress && other.blockchain == blockchain && other.address.equals(
            address, ignoreCase = true
        )
    }

    override fun hashCode(): Int {
        return Objects.hash(blockchain, address.lowercase())
    }
}

data class ContactNameAddress(
    val name: String,
    val contactAddress: ContactAddress
)
