package cash.p.terminal.ui.compose.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.market.MarketDataValue
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsImage
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.diffColor
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.models.Analytics.TechnicalAdvice.Advice
import cash.p.terminal.ui_compose.components.SectionItemBorderedRowUniversalClear

@Composable
fun MarketCoinClear(
    title: String,
    subtitle: String,
    coinIconUrl: String,
    alternativeCoinIconUrl: String? = null,
    coinIconPlaceholder: Int,
    value: String? = null,
    marketDataValue: MarketDataValue? = null,
    label: String? = null,
    onClick: (() -> Unit)? = null
) {
    SectionItemBorderedRowUniversalClear(
        onClick = onClick,
        borderBottom = true
    ) {
        HsImage(
            url = coinIconUrl,
            alternativeUrl = alternativeCoinIconUrl,
            placeholder = coinIconPlaceholder,
            modifier = Modifier
                .padding(end = 16.dp)
                .size(32.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            MarketCoinFirstRow(title, value)
            Spacer(modifier = Modifier.height(3.dp))
            MarketCoinSecondRow(subtitle, marketDataValue, label)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MarketCoin(
    title: String,
    subtitle: String,
    coinIconUrl: String,
    alternativeCoinIconUrl: String? = null,
    coinIconPlaceholder: Int,
    value: String? = null,
    marketDataValue: MarketDataValue? = null,
    label: String? = null,
    advice: Advice? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 24.dp)
            .combinedClickable(
                enabled = onClick != null || onLongClick != null,
                onClick = onClick ?: { },
                onLongClick = onLongClick
            )
            .background(ComposeAppTheme.colors.backgroundBase)
            .padding(horizontal = 16.dp)
            .padding(vertical = 12.dp),
        verticalAlignment =  Alignment.CenterVertically,
    ) {
        HsImage(
            url = coinIconUrl,
            alternativeUrl = alternativeCoinIconUrl,
            placeholder = coinIconPlaceholder,
            modifier = Modifier
                .padding(end = 16.dp)
                .size(32.dp)
                .clip(CircleShape)
        )
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            MarketCoinFirstRow(title, value, advice)
            Spacer(modifier = Modifier.height(3.dp))
            MarketCoinSecondRow(subtitle, marketDataValue, label)
        }
    }
}

@Composable
fun MarketCoinFirstRow(
    title: String,
    value: String?,
    advice: Advice? = null,
    badge: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            body_leah(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ComposeAppTheme.colors.badgeBackground)
                ) {
                    Text(
                        modifier = Modifier.padding(
                            start = 4.dp,
                            end = 4.dp,
                            bottom = 1.dp
                        ),
                        text = badge,
                        color = ComposeAppTheme.colors.textPrimary,
                        style = ComposeAppTheme.typography.microSB,
                        maxLines = 1,
                    )
                }
            }
            if (advice != null) {
                HSpacer(8.dp)
                SignalBadge(advice)
            }
        }
        body_leah(
            text = value ?: "n/a",
            maxLines = 1,
        )
    }
}

@Composable
fun SignalBadge(advice: Advice) {
    val textColor = when (advice) {
        Advice.Buy -> ComposeAppTheme.colors.statusSuccess
        Advice.Sell -> ComposeAppTheme.colors.statusError
        Advice.StrongBuy -> ComposeAppTheme.colors.backgroundBase
        Advice.StrongSell -> ComposeAppTheme.colors.backgroundBase
        Advice.Neutral -> ComposeAppTheme.colors.textPrimary
        else -> ComposeAppTheme.colors.brandDefault
    }

    val backgroundColor = when (advice) {
        Advice.Buy -> ComposeAppTheme.colors.statusSuccess20
        Advice.Sell -> ComposeAppTheme.colors.statusError20
        Advice.StrongBuy -> ComposeAppTheme.colors.statusSuccess
        Advice.StrongSell -> ComposeAppTheme.colors.statusError
        Advice.Neutral -> ComposeAppTheme.colors.badgeBackground
        else -> ComposeAppTheme.colors.statusWarning20
    }

    val text = when (advice) {
        Advice.Buy -> stringResource(R.string.Coin_Analytics_Indicators_Buy)
        Advice.Sell -> stringResource(R.string.Coin_Analytics_Indicators_Sell)
        Advice.StrongBuy -> stringResource(R.string.Coin_Analytics_Indicators_StrongBuy)
        Advice.StrongSell -> stringResource(R.string.Coin_Analytics_Indicators_StrongSell)
        Advice.Neutral -> stringResource(R.string.Coin_Analytics_Indicators_Neutral)
        else -> stringResource(R.string.Coin_Analytics_Indicators_Risky)
    }

    BadgeText(
        text = text,
        textColor = textColor,
        background = backgroundColor
    )
}

@Composable
fun MarketCoinSecondRow(
    subtitle: String,
    marketDataValue: MarketDataValue?,
    label: String?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        label?.let {
            Badge(
                modifier = Modifier.padding(end = 8.dp),
                text = it
            )
        }
        subhead2_grey(
            text = subtitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        MarketDataValueComponent(marketDataValue)
    }
}

@Composable
fun MarketDataValueComponent(marketDataValue: MarketDataValue?) {
    when (marketDataValue) {
        is MarketDataValue.MarketCap -> {
            Row {
                subhead2_grey(
                    text = marketDataValue.value,
                    maxLines = 1,
                )
            }
        }
        is MarketDataValue.Volume -> {
            Row {
                subhead2_grey(
                    text = marketDataValue.value,
                    maxLines = 1,
                )
            }
        }
        is MarketDataValue.Diff -> {
            Text(
                text = diffText(marketDataValue.value),
                color = diffColor(marketDataValue.value),
                style = ComposeAppTheme.typography.subhead2,
                maxLines = 1,
            )
        }
        is MarketDataValue.DiffNew -> {
            Text(
                text = formatValueAsDiff(marketDataValue.value),
                color = diffColor(marketDataValue.value.raw()),
                style = ComposeAppTheme.typography.subhead2,
                maxLines = 1,
            )
        }
        null -> {
            subhead2_grey(text = "---")
        }
    }
}

@Preview
@Composable
fun PreviewMarketCoin(){
    cash.p.terminal.ui_compose.theme.ComposeAppTheme {
        MarketCoin(
            title = "ETH",
            subtitle = "Ethereum With very long name for token",
            coinIconUrl = "eth.png",
            coinIconPlaceholder = R.drawable.logo_ethereum_24,
            value = "$2600",
        )
    }
}
