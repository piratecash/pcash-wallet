package cash.p.terminal.modules.metricchart

import cash.p.terminal.modules.chart.ChartCurrencyValueFormatterShortened
import cash.p.terminal.modules.chart.ChartNumberFormatterShortened
import cash.p.terminal.resources.CoinAnalytics_ActiveAddresses
import cash.p.terminal.resources.CoinAnalytics_CexVolume
import cash.p.terminal.resources.CoinAnalytics_DexLiquidity
import cash.p.terminal.resources.CoinAnalytics_DexVolume
import cash.p.terminal.resources.CoinAnalytics_ProjectTvl
import cash.p.terminal.resources.CoinAnalytics_TransactionCount
import cash.p.terminal.resources.Res
import io.horizontalsystems.chartview.chart.ChartModule
import io.horizontalsystems.core.IAppNumberFormatter
import org.jetbrains.compose.resources.StringResource

object ProChartModule {

    enum class ChartType(val titleRes: StringResource) {
        CexVolume(Res.string.CoinAnalytics_CexVolume),
        DexVolume(Res.string.CoinAnalytics_DexVolume),
        DexLiquidity(Res.string.CoinAnalytics_DexLiquidity),
        TxCount(Res.string.CoinAnalytics_TransactionCount),
        AddressesCount(Res.string.CoinAnalytics_ActiveAddresses),
        Tvl(Res.string.CoinAnalytics_ProjectTvl);

        fun numberFormatter(formatter: IAppNumberFormatter): ChartModule.ChartNumberFormatter = when (this) {
            CexVolume,
            DexVolume,
            Tvl,
            DexLiquidity -> ChartCurrencyValueFormatterShortened(formatter)
            TxCount,
            AddressesCount -> ChartNumberFormatterShortened(formatter)
        }
    }

}
