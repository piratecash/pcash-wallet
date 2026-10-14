package cash.p.terminal.modules.metricchart

import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize

@CommonParcelize
enum class MetricsType : CommonParcelable {
    TotalMarketCap, Volume24h, Etf, TvlInDefi;
}
