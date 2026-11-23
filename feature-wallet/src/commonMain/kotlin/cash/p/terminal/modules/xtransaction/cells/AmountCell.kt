package cash.p.terminal.modules.xtransaction.cells

import androidx.compose.foundation.Image
import cash.p.terminal.resources.nft_address
import cash.p.terminal.resources.CoinPlatforms_Native
import cash.p.terminal.resources.Res
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.modules.coin.CoinPage
import cash.p.terminal.modules.xtransaction.helpers.TransactionInfoHelper
import cash.p.terminal.modules.xtransaction.helpers.coinAmountString
import cash.p.terminal.modules.xtransaction.helpers.coinIconPainter
import cash.p.terminal.modules.xtransaction.helpers.fiatAmountString
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui_compose.CoinFragmentInput
import cash.p.terminal.ui_compose.components.HFillSpacer
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.components.CellUniversal
import cash.p.terminal.ui.compose.components.coinIconPlate

@Composable
fun AmountCell(
    title: String,
    coinIcon: Painter,
    coinProtocolType: String?,
    coinAmount: String,
    coinAmountColor: Color,
    fiatAmount: String?,
    onClick: (() -> Unit)?,
    iconPlate: Boolean,
    borderTop: Boolean = true
) {
    CellUniversal(
        borderTop = borderTop,
        onClick = onClick
    ) {
        Image(
            painter = coinIcon,
            contentDescription = null,
            modifier = Modifier.size(32.dp).coinIconPlate(enabled = iconPlate),
            colorFilter = null,
            contentScale = ContentScale.Fit
        )

        HSpacer(16.dp)
        Column {
            subhead2_leah(text = title)
            coinProtocolType?.let {
                VSpacer(height = 1.dp)
                caption_grey(text = it)
            }
        }
        HFillSpacer(minWidth = 8.dp)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = coinAmount,
                style = ComposeAppTheme.typography.subhead1,
                color = coinAmountColor,
            )

            fiatAmount?.let {
                VSpacer(height = 1.dp)
                subhead2_grey(text = it)
            }
        }
    }
}

@Composable
fun AmountCellTV(
    title: String,
    transactionValue: TransactionValue,
    coinAmountColor: AmountColor,
    coinAmountSign: AmountSign,
    transactionInfoHelper: TransactionInfoHelper,
    navigation: HSNavigation,
    borderTop: Boolean = true
) {
    val nft = transactionValue as? TransactionValue.NftValue
    AmountCell(
        title = title,
        coinIcon = coinIconPainter(
            url = transactionValue.coinIconUrl,
            alternativeUrl = transactionValue.alternativeCoinIconUrl,
            placeholder = transactionValue.coinIconPlaceholder
        ),
        iconPlate = transactionValue !is TransactionValue.NftValue,
        coinProtocolType = if (nft != null) {
            nft.tokenName
        } else {
            transactionValue.badge ?: stringResource(Res.string.CoinPlatforms_Native)
        },
        coinAmount = coinAmountString(
            value = transactionValue.decimalValue?.abs(),
            coinCode = transactionValue.coinCode,
            coinDecimals = transactionValue.decimals,
            sign = coinAmountSign.sign()
        ),
        coinAmountColor = coinAmountColor.color(),
        fiatAmount = if (nft != null) {
            null
        } else {
            fiatAmountString(
                value = transactionInfoHelper.getXRate(transactionValue.coinUid)
                    ?.let {
                        transactionValue.decimalValue?.abs()
                            ?.multiply(it)
                    },
                fiatSymbol = transactionInfoHelper.getCurrencySymbol()
            )
        },
        onClick = if (nft != null) {
            null
        } else {
            { navigation.slideFromRight(CoinPage(CoinFragmentInput(transactionValue.coinUid))) }
        },
        borderTop = borderTop,
    )
    nft?.let {
        AddressCell(
            title = stringResource(Res.string.nft_address),
            value = it.mintAddress,
            showAddContactButton = false,
            blockchainType = null,
        )
    }
}

enum class AmountSign {
    Plus, Minus, None;

    fun sign() = when (this) {
        Plus -> "+"
        Minus -> "-"
        None -> ""
    }
}

enum class AmountColor {
    Positive, Negative, Neutral;

    @Composable
    fun color() = when (this) {
        Positive -> ComposeAppTheme.colors.statusSuccess
        Negative -> ComposeAppTheme.colors.textPrimary
        Neutral -> ComposeAppTheme.colors.textPrimary
    }
}
