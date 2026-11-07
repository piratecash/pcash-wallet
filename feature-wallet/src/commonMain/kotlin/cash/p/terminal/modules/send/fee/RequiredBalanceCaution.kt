package cash.p.terminal.modules.send.fee

import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.check_fee_warning
import cash.p.terminal.resources.insufficient_balance_required_available
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.Token
import java.math.BigDecimal

/** Unknown balance only warns; a known balance below [required] blocks the transaction. */
fun requiredBalanceCaution(
    available: BigDecimal?,
    required: BigDecimal,
    feeToken: Token,
): CautionViewItem? = when {
    available == null -> caution(
        TranslatableString.ResString(Res.string.check_fee_warning, required.withCode(feeToken)),
        CautionViewItem.Type.Warning,
    )

    available < required -> caution(
        insufficientRequiredBalanceText(required, available, feeToken),
        CautionViewItem.Type.Error,
    )

    else -> null
}

fun insufficientRequiredBalanceText(
    required: BigDecimal,
    available: BigDecimal,
    feeToken: Token,
) = TranslatableString.ResString(
    Res.string.insufficient_balance_required_available,
    feeToken.coin.code,
    required.withCode(feeToken),
    available.withCode(feeToken),
)

private fun BigDecimal.withCode(token: Token) = stripTrailingZeros().toPlainString() + " " + token.coin.code

private fun caution(text: TranslatableString, type: CautionViewItem.Type) =
    CautionViewItem(title = text.toString(), text = "", type = type)
