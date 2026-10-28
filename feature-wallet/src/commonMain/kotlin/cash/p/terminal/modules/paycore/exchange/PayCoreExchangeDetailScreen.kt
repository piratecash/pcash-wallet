package cash.p.terminal.modules.paycore.exchange

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.modules.fee.QuoteInfoRow
import cash.p.terminal.network.changenow.domain.entity.TransactionStatusEnum
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.CellUniversal
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.LocalUrlOpener
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.components.subhead2_lucian
import cash.p.terminal.ui_compose.components.subhead2_remus
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Button_Copy
import cash.p.terminal.resources.Hud_Text_Copied
import cash.p.terminal.resources.Swap
import cash.p.terminal.resources.Swap_Price
import cash.p.terminal.resources.Swap_YouGet
import cash.p.terminal.resources.Swap_YouPay
import cash.p.terminal.resources.TransactionInfo_Date
import cash.p.terminal.resources.TransactionInfo_Id
import cash.p.terminal.resources.TransactionInfo_Status
import cash.p.terminal.resources.Transactions_Failed
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.resources.ic_arrow_swap3_20
import cash.p.terminal.resources.ic_checkmark_20
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.resources.ic_copy_20
import cash.p.terminal.resources.ic_support_24
import cash.p.terminal.resources.multi_swap_completed
import cash.p.terminal.resources.paycore_service
import cash.p.terminal.resources.paycore_support
import cash.p.terminal.resources.swap_duration
import cash.p.terminal.resources.transaction_swap_status_refunded
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import io.horizontalsystems.core.Translator

@Composable
internal fun PayCoreExchangeDetailScreen(
    uiState: PayCoreExchangeDetailUiState?,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.Swap),
                navigationIcon = { HsBackButton(onClick = onBack) },
            )
        }
    ) { paddingValues ->
        val state = uiState ?: return@Scaffold

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            VSpacer(height = 12.dp)

            SectionUniversalLawrence {
                AmountRow(
                    title = stringResource(Res.string.Swap_YouPay),
                    amount = state.amountSentFormatted,
                    isNegative = true,
                    borderTop = false,
                )
                AmountRow(
                    title = stringResource(Res.string.Swap_YouGet),
                    amount = state.amountGotFormatted,
                    isNegative = false,
                    borderTop = true,
                )
                ServiceRow()
                PriceRow(
                    priceFormatted = state.priceFormatted,
                    priceInvFormatted = state.priceInvFormatted,
                )
                DateRow(dateFormatted = state.dateFormatted)
                state.durationFormatted?.let { DurationRow(durationFormatted = it) }
                StatusRow(status = state.status)
                TransactionIdRow(transactionId = state.transactionId)
            }

            VSpacer(height = 16.dp)

            SectionUniversalLawrence {
                SupportRow(url = state.supportUrl)
            }

            VSpacer(height = 32.dp)
        }
    }
}

@Composable
private fun AmountRow(
    title: String,
    amount: String,
    isNegative: Boolean,
    borderTop: Boolean,
) {
    QuoteInfoRow(
        borderTop = borderTop,
        title = { subhead2_grey(text = title) },
        value = {
            if (isNegative) {
                subhead2_lucian(text = amount, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                subhead2_remus(text = amount, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    )
}

@Composable
private fun ServiceRow() {
    QuoteInfoRow(
        borderTop = true,
        title = { subhead2_grey(text = stringResource(Res.string.paycore_service)) },
        value = { subhead2_leah(text = SwapProvider.PAYCORE.title) }
    )
}

@Composable
private fun PriceRow(
    priceFormatted: String,
    priceInvFormatted: String,
) {
    var showRegular by remember { mutableStateOf(true) }
    QuoteInfoRow(
        borderTop = true,
        title = { subhead2_grey(text = stringResource(Res.string.Swap_Price)) },
        value = {
            Row(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showRegular = !showRegular }
                )
            ) {
                subhead2_leah(
                    text = if (showRegular) priceFormatted else priceInvFormatted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
                HSpacer(width = 8.dp)
                Icon(
                    painter = painterResource(Res.drawable.ic_arrow_swap3_20),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.iconSecondary,
                )
            }
        }
    )
}

@Composable
private fun DateRow(dateFormatted: String) {
    QuoteInfoRow(
        borderTop = true,
        title = { subhead2_grey(text = stringResource(Res.string.TransactionInfo_Date)) },
        value = { subhead2_leah(text = dateFormatted) }
    )
}

@Composable
private fun DurationRow(durationFormatted: String) {
    QuoteInfoRow(
        borderTop = true,
        title = { subhead2_grey(text = stringResource(Res.string.swap_duration)) },
        value = { subhead2_leah(text = durationFormatted) }
    )
}

@Composable
private fun StatusRow(status: TransactionStatusEnum) {
    QuoteInfoRow(
        borderTop = true,
        title = { subhead2_grey(text = stringResource(Res.string.TransactionInfo_Status)) },
        value = {
            when (status) {
                TransactionStatusEnum.FINISHED -> {
                    Icon(
                        painter = painterResource(Res.drawable.ic_checkmark_20),
                        contentDescription = null,
                        tint = ComposeAppTheme.colors.statusSuccess,
                    )
                    HSpacer(width = 4.dp)
                    subhead2_remus(text = stringResource(Res.string.multi_swap_completed))
                }
                TransactionStatusEnum.FAILED -> {
                    Icon(
                        painter = painterResource(Res.drawable.ic_close_24),
                        contentDescription = null,
                        tint = ComposeAppTheme.colors.statusError,
                    )
                    HSpacer(width = 4.dp)
                    subhead2_lucian(text = stringResource(Res.string.Transactions_Failed))
                }
                TransactionStatusEnum.REFUNDED -> {
                    subhead2_lucian(text = stringResource(Res.string.transaction_swap_status_refunded))
                }
                else -> {
                    subhead2_leah(text = status.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }
    )
}

@Composable
private fun TransactionIdRow(transactionId: String) {
    val hud = rememberHudMessenger()
    CellUniversal(borderTop = true) {
        subhead2_grey(text = stringResource(Res.string.TransactionInfo_Id))
        HSpacer(width = 16.dp)
        subhead2_leah(
            modifier = Modifier.weight(1f),
            text = transactionId,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
        )
        HSpacer(width = 8.dp)
        ButtonSecondaryCircle(
            icon = Res.drawable.ic_copy_20,
            contentDescription = stringResource(Res.string.Button_Copy),
            onClick = {
                getKoinInstance<IClipboardManager>().copyText(transactionId)
                hud.showSuccess(Translator.getString(Res.string.Hud_Text_Copied))
            }
        )
    }
}

@Composable
private fun SupportRow(url: String) {
    val urlOpener = LocalUrlOpener.current
    CellUniversal(
        borderTop = false,
        onClick = { urlOpener.open(url) },
    ) {
        Icon(
            painter = painterResource(Res.drawable.ic_support_24),
            contentDescription = null,
            tint = ComposeAppTheme.colors.brandDefault,
        )
        HSpacer(width = 16.dp)
        body_leah(
            modifier = Modifier.weight(1f),
            text = stringResource(Res.string.paycore_support),
        )
        HSpacer(width = 8.dp)
        Icon(
            painter = painterResource(Res.drawable.ic_arrow_right),
            contentDescription = null,
            tint = ComposeAppTheme.colors.iconSecondary,
        )
    }
}
