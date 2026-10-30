package cash.p.terminal.modules.swapprogress

import cash.p.terminal.core.orHide
import cash.p.terminal.entities.SwapProviderTransaction
import cash.p.terminal.entities.UnstoppableProviderTitles
import cash.p.terminal.modules.transactionInfo.TransactionInfoViewItem
import cash.p.terminal.modules.transactionInfo.TransactionViewItemFactoryHelper
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.modules.transactions.toUniversalStatus
import cash.p.terminal.network.changenow.domain.entity.toStatus
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableTrack
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableTrackLeg
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Swap_YouGet
import cash.p.terminal.resources.offline_transaction_amount
import cash.p.terminal.resources.swap_progress_action_required_aml
import cash.p.terminal.resources.swap_progress_action_required_default
import cash.p.terminal.resources.swap_progress_action_required_frozen
import cash.p.terminal.resources.swap_progress_action_required_overdue
import cash.p.terminal.resources.swap_progress_contact_support
import cash.p.terminal.resources.swap_progress_leg_receive
import cash.p.terminal.resources.swap_progress_leg_send
import cash.p.terminal.resources.swap_progress_leg_swap
import cash.p.terminal.resources.swap_progress_leg_transfer
import cash.p.terminal.resources.swap_progress_step_header
import cash.p.terminal.resources.swap_support_email_deposit_address
import cash.p.terminal.resources.swap_support_email_deposit_tx
import cash.p.terminal.resources.swap_you_send
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.Translator
import java.math.BigDecimal
import org.jetbrains.compose.resources.StringResource

data class SwapProgressSummary(val currentStep: Int, val totalSteps: Int, val allCompleted: Boolean)

fun UnstoppableTrack.progressSummary(): SwapProgressSummary? {
    if (legs.isEmpty()) return null
    val firstOpen = legs.indexOfFirst { it.status != UnstoppableTrackLeg.Status.Completed }
    return SwapProgressSummary(
        currentStep = if (firstOpen < 0) legs.size else firstOpen + 1,
        totalSteps = legs.size,
        allCompleted = firstOpen < 0,
    )
}

/** Unstoppable has nothing left to fill in: a leg failed, or every leg is completed with its hash and amount. */
fun UnstoppableTrack.isSettled(depositHash: String?): Boolean =
    legs.isNotEmpty() && (legs.any { it.status == UnstoppableTrackLeg.Status.Failed } ||
        legs.indices.all { index ->
            legs[index].status == UnstoppableTrackLeg.Status.Completed &&
                legs[index].resultAmount != null &&
                legs.hashAt(index, depositHash) != null
        })

class UnstoppableSwapStepsFactory(
    private val numberFormatter: IAppNumberFormatter,
    private val providerTitles: UnstoppableProviderTitles,
    private val legExplorer: SwapLegExplorer,
) {

    fun sections(
        swap: SwapProviderTransaction,
        track: UnstoppableTrack?,
        contacts: List<SupportContact>,
        hideAmount: Boolean,
    ): List<List<TransactionInfoViewItem>> {
        val legs = track?.legs.orEmpty()
        val steps = if (legs.isEmpty()) {
            listOf(listOf(TransactionInfoViewItem.Status(swap.status.toStatus().toUniversalStatus())))
        } else {
            val payout = track?.toAmount?.takeIf { it > BigDecimal.ZERO } ?: swap.amountOutReal
            legs.indices.flatMap { index -> legSections(swap, payout, legs, index, hideAmount) }
        }
        val support = supportSectionItems(
            providerTitles.title(swap.subProviderId).orEmpty(),
            contacts,
            emailBody(legs.takeIf { it.isNotEmpty() }?.hashAt(0, swap.depositTransactionHash), legs.firstOrNull()?.toAddress),
        )
        return listOfNotNull(track?.let(::banner)) + steps + listOfNotNull(support.takeIf { it.isNotEmpty() })
    }

    private fun banner(track: UnstoppableTrack): List<TransactionInfoViewItem>? {
        if (!track.actionRequired) return null
        val reason = Translator.getString(
            when (track.pauseReason) {
                "aml" -> Res.string.swap_progress_action_required_aml
                "frozen" -> Res.string.swap_progress_action_required_frozen
                "overdue_with_funds" -> Res.string.swap_progress_action_required_overdue
                else -> Res.string.swap_progress_action_required_default
            }
        )
        val message = "$reason ${Translator.getString(Res.string.swap_progress_contact_support)}"
        return listOf(TransactionInfoViewItem.WarningMessage(message))
    }

    private fun legSections(
        swap: SwapProviderTransaction,
        payout: BigDecimal?,
        legs: List<UnstoppableTrackLeg>,
        index: Int,
        hideAmount: Boolean,
    ): List<List<TransactionInfoViewItem>> {
        val leg = legs[index]
        val header = Translator.getString(Res.string.swap_progress_step_header, index + 1, legTitle(legs, index))
        val card = buildList {
            add(TransactionInfoViewItem.Status(leg.status.toTransactionStatus()))
            addAll(amountRows(leg, expectedOut(swap, payout, legs, index), hideAmount))
            legs.hashAt(index, swap.depositTransactionHash)?.let { hash ->
                add(TransactionInfoViewItem.TransactionHash(hash.orHide(hideAmount)))
                legExplorer.explorer(leg.chainId, swap.accountId, hash)?.let {
                    addAll(TransactionViewItemFactoryHelper.getExplorerSectionItems(it))
                }
            }
        }
        return listOf(listOf(TransactionInfoViewItem.Description(header)), card)
    }

    // Only legs that deliver the final coin can fall back to the reported payout or the quote.
    private fun expectedOut(
        swap: SwapProviderTransaction,
        payout: BigDecimal?,
        legs: List<UnstoppableTrackLeg>,
        index: Int,
    ): ExpectedAmount? {
        if (!legs.isReceive(index) && !legs.isFinalSwap(index)) return null
        return payout?.let { ExpectedAmount(it, quote = false) } ?: ExpectedAmount(swap.amountOut, quote = true)
    }

    private fun legTitle(legs: List<UnstoppableTrackLeg>, index: Int): String {
        val leg = legs[index]
        return when {
            leg.type == UnstoppableTrackLeg.Type.Swap -> Translator.getString(
                Res.string.swap_progress_leg_swap, leg.fromAsset.assetCode(), leg.toAsset.assetCode()
            )

            legs.isReceive(index) ->
                Translator.getString(Res.string.swap_progress_leg_receive, leg.toAsset.assetCode())

            leg.type == UnstoppableTrackLeg.Type.NativeSend && index == legs.indexOfFirst { it.type == leg.type } ->
                Translator.getString(Res.string.swap_progress_leg_send, leg.fromAsset.assetCode())

            else -> Translator.getString(Res.string.swap_progress_leg_transfer, leg.fromAsset.assetCode())
        }
    }

    private fun amountRows(
        leg: UnstoppableTrackLeg,
        expected: ExpectedAmount?,
        hideAmount: Boolean,
    ): List<TransactionInfoViewItem> = buildList {
        fun row(title: StringResource, legAmount: BigDecimal?, asset: String?, fallback: ExpectedAmount? = null) {
            val amount = legAmount ?: fallback?.amount ?: return
            val quote = legAmount == null && fallback?.quote == true
            add(
                TransactionInfoViewItem.ValueClickable(
                    Translator.getString(title),
                    formatAmount(amount, asset, approximate = quote || leg.status != UnstoppableTrackLeg.Status.Completed, hideAmount),
                )
            )
        }
        if (leg.type == UnstoppableTrackLeg.Type.Swap) {
            row(Res.string.swap_you_send, leg.fromAmount, leg.fromAsset)
            row(Res.string.Swap_YouGet, leg.resultAmount, leg.toAsset, expected)
        } else {
            row(Res.string.offline_transaction_amount, leg.resultAmount, leg.fromAsset ?: leg.toAsset, expected)
        }
    }

    private fun formatAmount(amount: BigDecimal, asset: String?, approximate: Boolean, hideAmount: Boolean): String {
        val formatted = numberFormatter.formatCoinFull(amount, asset.assetCode(), 8)
        return (if (approximate) "≈ $formatted" else formatted).orHide(hideAmount)
    }

    private fun emailBody(depositHash: String?, depositAddress: String?): String = listOfNotNull(
        depositHash?.let { Translator.getString(Res.string.swap_support_email_deposit_tx, it) },
        depositAddress?.takeIf { it.isNotBlank() }
            ?.let { Translator.getString(Res.string.swap_support_email_deposit_address, it) },
    ).joinToString("\n")

    private class ExpectedAmount(val amount: BigDecimal, val quote: Boolean)
}

// Unstoppable may omit the deposit hash, but the deposit is our own transaction.
private fun List<UnstoppableTrackLeg>.hashAt(index: Int, depositHash: String?): String? =
    this[index].hash ?: depositHash.takeIf { isDeposit(index) }

private val UnstoppableTrackLeg.resultAmount: BigDecimal?
    get() = if (type == UnstoppableTrackLeg.Type.Swap) toAmount else fromAmount ?: toAmount

private fun List<UnstoppableTrackLeg>.isDeposit(index: Int) =
    index == 0 && this[index].type == UnstoppableTrackLeg.Type.NativeSend

private fun List<UnstoppableTrackLeg>.isReceive(index: Int) =
    index > 0 && index == indexOfLast { it.type == UnstoppableTrackLeg.Type.NativeSend }

private fun List<UnstoppableTrackLeg>.isFinalSwap(index: Int) =
    index == indexOfLast { it.type == UnstoppableTrackLeg.Type.Swap }

private fun UnstoppableTrackLeg.Status.toTransactionStatus() = when (this) {
    UnstoppableTrackLeg.Status.NotStarted -> TransactionStatus.Pending
    UnstoppableTrackLeg.Status.InProgress -> TransactionStatus.Processing(0.5f)
    UnstoppableTrackLeg.Status.Completed -> TransactionStatus.Completed
    UnstoppableTrackLeg.Status.Failed -> TransactionStatus.Failed
}

/** `CHAIN.TICKER[-CONTRACT]` -> `TICKER`. */
private fun String?.assetCode(): String = orEmpty().substringAfter('.').substringBefore('-')
