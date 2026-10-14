package cash.p.terminal.wallet.entities

import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize

@CommonParcelize
data class UsedAddress(
    val index: Int,
    val address: String,
    val explorerUrl: String
): CommonParcelable
