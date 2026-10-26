package cash.p.terminal.modules.transactionInfo.options

import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize

@CommonParcelize
enum class SpeedUpCancelType : CommonParcelable {
    SpeedUp,
    // Cancel // MOBILE-593
}
