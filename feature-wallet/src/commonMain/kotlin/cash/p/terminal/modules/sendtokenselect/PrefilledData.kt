package cash.p.terminal.modules.sendtokenselect

import cash.p.terminal.entities.AddressUri
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import java.math.BigDecimal

@CommonParcelize
data class PrefilledData(
    val address: String?,
    val amount: BigDecimal? = null,
    val memo: String? = null,
) : CommonParcelable {
    companion object {
        fun from(addressUri: AddressUri) = PrefilledData(
            address = addressUri.address,
            amount = addressUri.amount,
            memo = addressUri.value(AddressUri.Field.Memo),
        )
    }
}
