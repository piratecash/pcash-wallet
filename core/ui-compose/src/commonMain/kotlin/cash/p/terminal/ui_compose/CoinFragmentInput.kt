package cash.p.terminal.ui_compose

import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize

@CommonParcelize
data class CoinFragmentInput(val coinUid: String) : CommonParcelable
