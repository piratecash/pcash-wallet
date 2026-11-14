package cash.p.terminal.modules.send.tron

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.orHide
import cash.p.terminal.modules.fee.QuoteInfoRow
import cash.p.terminal.modules.multiswap.ui.DataField
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.FeeInfo_TronResourcesConsumed_Description
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.tron_fee_covered_free
import cash.p.terminal.resources.tron_fee_covered_staked
import cash.p.terminal.resources.tron_fee_energy_contract
import cash.p.terminal.resources.tron_fee_energy_own
import cash.p.terminal.resources.tron_resource_available_of
import cash.p.terminal.resources.tron_resource_bandwidth
import cash.p.terminal.resources.tron_resource_energy
import cash.p.terminal.resources.tron_resource_free_bandwidth
import cash.p.terminal.resources.tron_resource_staked_bandwidth
import cash.p.terminal.resources.tron_resources_title
import cash.p.terminal.ui.compose.components.CardsSwapInfo
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.InfoBottomSheet
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.tronkit.models.AccountResources
import io.horizontalsystems.tronkit.transaction.Fee
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import java.math.BigDecimal

data class TronResourceRow(val title: StringResource, val available: String, val limit: String)

data class TronFeeRow(val title: StringResource, val amount: String, val details: List<TronFeeDetail>)

/** A detail without [text] shows [value] as is. */
data class TronFeeDetail(val text: StringResource?, val value: String = "")

data class DataFieldTronResources(val rows: List<TronResourceRow>) : DataField {
    @Composable
    override fun GetContent(navigation: HSNavigation, borderTop: Boolean) = TronResourceRows(rows, borderTop)
}

data class DataFieldTronFee(val rows: List<TronFeeRow>) : DataField {
    @Composable
    override fun GetContent(navigation: HSNavigation, borderTop: Boolean) = TronFeeRows(rows, borderTop)
}

/** Limits follow the staked TRX, so [hidden] masks them together with the balance. */
fun AccountResources.toTronResourceRows(formatter: IAppNumberFormatter, hidden: Boolean): List<TronResourceRow> =
    listOfNotNull(
        energy.toRow(Res.string.tron_resource_energy, formatter, hidden),
        freeBandwidth.toRow(Res.string.tron_resource_free_bandwidth, formatter, hidden),
        stakedBandwidth.toRow(Res.string.tron_resource_staked_bandwidth, formatter, hidden),
    )

fun TronFeeBreakdown.toTronFeeRows(
    formatter: IAppNumberFormatter,
    coinCode: String,
    decimals: Int,
): List<TronFeeRow> {
    val burnedDetail = { burned: BigDecimal ->
        burned.takeIf { it.signum() > 0 }?.let { TronFeeDetail(null, formatter.formatCoinFull(it, coinCode, decimals)) }
    }
    val energyRow = energy?.takeIf { it.required > 0 }?.let {
        TronFeeRow(
            title = Res.string.tron_resource_energy,
            amount = formatter.formatResource(it.required),
            details = listOfNotNull(
                it.fromAccount.toDetail(Res.string.tron_fee_energy_own, formatter),
                it.fromContract.toDetail(Res.string.tron_fee_energy_contract, formatter),
                burnedDetail(it.burned),
            ),
        )
    }
    val bandwidthRow = bandwidth?.takeIf { it.points > 0 }?.let {
        val detail = when (it.source) {
            Fee.Bandwidth.Source.Free -> TronFeeDetail(Res.string.tron_fee_covered_free)
            Fee.Bandwidth.Source.Staked -> TronFeeDetail(Res.string.tron_fee_covered_staked)
            Fee.Bandwidth.Source.Burn -> burnedDetail(it.burned)
        }
        TronFeeRow(Res.string.tron_resource_bandwidth, formatter.formatResource(it.points), listOfNotNull(detail))
    }
    return listOfNotNull(energyRow, bandwidthRow)
}

private fun AccountResources.Resource.toRow(title: StringResource, formatter: IAppNumberFormatter, hidden: Boolean) =
    takeIf { it.limit > 0 }?.let {
        TronResourceRow(
            title,
            formatter.formatResource(it.available).orHide(hidden),
            formatter.formatResource(it.limit).orHide(hidden),
        )
    }

private fun Long.toDetail(text: StringResource, formatter: IAppNumberFormatter) =
    takeIf { it > 0 }?.let { TronFeeDetail(text, formatter.formatResource(it)) }

internal fun IAppNumberFormatter.formatResource(value: Long) = format(value.toBigDecimal(), 0, 0)

@Composable
fun TronResourcesSection(resources: AccountResources?, balanceHidden: Boolean) {
    val rows = remember(resources, balanceHidden) {
        resources?.toTronResourceRows(getKoinInstance(), balanceHidden).orEmpty()
    }
    TronResourcesCard(rows)
}

@Composable
fun TronFeeBreakdownRows(breakdown: TronFeeBreakdown?, coinCode: String, decimals: Int) {
    val rows = remember(breakdown) { breakdown?.toTronFeeRows(getKoinInstance(), coinCode, decimals).orEmpty() }
    TronFeeRows(rows, borderTop = true)
}

@Composable
private fun TronResourcesCard(rows: List<TronResourceRow>) {
    if (rows.isEmpty()) return
    val title = stringResource(Res.string.tron_resources_title)
    var showInfo by remember { mutableStateOf(false) }

    HeaderText(text = title, onInfoClick = { showInfo = true })
    CardsSwapInfo {
        TronResourceRows(rows, borderTop = false)
    }
    VSpacer(height = 12.dp)

    if (showInfo) {
        InfoBottomSheet(
            title = title,
            text = stringResource(Res.string.FeeInfo_TronResourcesConsumed_Description),
            onDismiss = { showInfo = false }
        )
    }
}

@Composable
private fun TronResourceRows(rows: List<TronResourceRow>, borderTop: Boolean) {
    rows.forEachIndexed { index, row ->
        QuoteInfoRow(
            borderTop = borderTop || index > 0,
            title = { subhead2_grey(text = stringResource(row.title)) },
            value = {
                subhead2_leah(text = stringResource(Res.string.tron_resource_available_of, row.available, row.limit))
            }
        )
    }
}

@Composable
private fun TronFeeRows(rows: List<TronFeeRow>, borderTop: Boolean) {
    rows.forEachIndexed { index, row ->
        QuoteInfoRow(
            borderTop = borderTop || index > 0,
            title = { subhead2_grey(text = stringResource(row.title)) },
            value = {
                Column(horizontalAlignment = Alignment.End) {
                    subhead2_leah(text = row.amount)
                    if (row.details.isNotEmpty()) {
                        VSpacer(height = 1.dp)
                        val details = row.details.map { detail ->
                            detail.text?.let { stringResource(it, detail.value) } ?: detail.value
                        }
                        subhead2_grey(text = details.joinToString(DETAILS_SEPARATOR))
                    }
                }
            }
        )
    }
}

private const val DETAILS_SEPARATOR = " · "

@Preview
@Composable
private fun TronResourcesCardPreview() {
    ComposeAppTheme {
        Column {
            TronResourcesCard(
                rows = listOf(
                    TronResourceRow(Res.string.tron_resource_energy, "20,000", "65,000"),
                    TronResourceRow(Res.string.tron_resource_free_bandwidth, "255", "600"),
                    TronResourceRow(Res.string.tron_resource_staked_bandwidth, "1,200", "1,500"),
                )
            )
        }
    }
}

@Preview
@Composable
private fun TronFeeRowsPreview() {
    ComposeAppTheme {
        CardsSwapInfo {
            TronFeeRows(
                rows = listOf(
                    TronFeeRow(
                        Res.string.tron_resource_energy,
                        "64,895",
                        listOf(
                            TronFeeDetail(Res.string.tron_fee_energy_own, "20,000"),
                            TronFeeDetail(Res.string.tron_fee_energy_contract, "10,000"),
                            TronFeeDetail(null, "3.4895 TRX"),
                        )
                    ),
                    TronFeeRow(
                        Res.string.tron_resource_bandwidth,
                        "345",
                        listOf(TronFeeDetail(Res.string.tron_fee_covered_free)),
                    ),
                ),
                borderTop = false,
            )
        }
    }
}

@Preview
@Composable
private fun TronFeeRowsBurnedPreview() {
    ComposeAppTheme {
        CardsSwapInfo {
            TronFeeRows(
                rows = listOf(
                    TronFeeRow(Res.string.tron_resource_bandwidth, "538", listOf(TronFeeDetail(null, "0.1 TRX"))),
                ),
                borderTop = false,
            )
        }
    }
}
