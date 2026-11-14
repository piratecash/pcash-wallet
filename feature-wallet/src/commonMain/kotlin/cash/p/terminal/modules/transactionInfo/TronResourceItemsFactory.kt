package cash.p.terminal.modules.transactionInfo

import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.orHide
import cash.p.terminal.entities.transactionrecords.tron.TronTransactionRecord
import cash.p.terminal.modules.send.tron.formatResource
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.tron_fee_energy_contract
import cash.p.terminal.resources.tron_fee_energy_own
import cash.p.terminal.resources.tron_resource_bandwidth
import cash.p.terminal.resources.tron_resource_energy
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.Translator
import org.jetbrains.compose.resources.StringResource

object TronResourceItemsFactory {
    private const val UNKNOWN_RESOURCE = "\u2014"
    private val numberFormatter: IAppNumberFormatter
        get() = getKoinInstance()

    fun getItems(record: TronTransactionRecord, hideSensitiveInfo: Boolean): List<TransactionInfoViewItem> {
        if (!record.isOwnOutgoing) return emptyList()
        val tx = record.transaction
        val known = !record.isReceiptMissing
        val energy = Translator.getString(Res.string.tron_resource_energy) to
                tx.energyUsageTotal.takeIf { known }?.let { total ->
                    tronResourceValue(
                        total,
                        listOfNotNull(
                            resourceDetail(Res.string.tron_fee_energy_own, tx.energyUsage),
                            resourceDetail(Res.string.tron_fee_energy_contract, tx.originEnergyUsage),
                            tronBurned(record, tx.energyFee),
                        )
                    )
                }
        val bandwidth = Translator.getString(Res.string.tron_resource_bandwidth) to
                tx.netUsage.takeIf { known }?.let { points ->
                    // The receipt does not say whether free or staked quota covered the points.
                    tronResourceValue(points.positiveOrNull(), listOfNotNull(tronBurned(record, tx.netFee)))
                }
        val rows = if (record.isSmartContractCall) listOf(energy, bandwidth) else listOf(bandwidth)
        return rows.map { (title, value) ->
            TransactionInfoViewItem.Value(title, (value ?: UNKNOWN_RESOURCE).orHide(hideSensitiveInfo))
        }
    }

    private fun tronResourceValue(points: Long?, details: List<String>) =
        (listOfNotNull(points?.let(numberFormatter::formatResource)) + details)
            .joinToString(" · ")
            .ifEmpty { UNKNOWN_RESOURCE }

    private fun resourceDetail(text: StringResource, points: Long?): String? =
        points?.positiveOrNull()?.let { Translator.getString(text, numberFormatter.formatResource(it)) }

    private fun tronBurned(record: TronTransactionRecord, sun: Long?): String? =
        sun?.positiveOrNull()?.let {
            val amount = it.toBigDecimal().movePointLeft(record.token.decimals).stripTrailingZeros()
            numberFormatter.formatCoinFull(amount, record.token.coin.code, record.token.decimals)
        }

    private fun Long.positiveOrNull() = takeIf { it > 0 }
}
