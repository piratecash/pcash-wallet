package cash.p.terminal.featureStacking.ui.calculatorScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.featureStacking.ui.staking.StackingType
import cash.p.terminal.network.pirate.domain.enity.PeriodType
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.day_period
import cash.p.terminal.resources.done
import cash.p.terminal.resources.enter_coin_amount
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.resources.month_period
import cash.p.terminal.resources.period
import cash.p.terminal.resources.pos_calculator
import cash.p.terminal.resources.pos_calculator_cosanta_description
import cash.p.terminal.resources.pos_calculator_pirate_description
import cash.p.terminal.resources.week_period
import cash.p.terminal.resources.year_period
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.InputField
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val MinCalculatorRowHeight = 40.dp

@Composable
internal fun CalculatorScreen(
    uiState: CalculatorUIState,
    onValueChanged: (String) -> Unit,
    onDoneClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(modifier = Modifier.padding(start = 16.dp, bottom = 16.dp, top = 29.dp)) {
            Text(
                text = stringResource(Res.string.pos_calculator),
                style = ComposeAppTheme.typography.body,
                color = ComposeAppTheme.colors.textPrimary,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(Res.drawable.ic_close_24),
                contentDescription = "close icon",
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDoneClicked
                    ),
                tint = ComposeAppTheme.colors.iconSecondary
            )
        }
        val descriptionRes = if (uiState.stackingType == StackingType.PCASH) {
            Res.string.pos_calculator_pirate_description
        } else {
            Res.string.pos_calculator_cosanta_description
        }
        Text(
            text = stringResource(descriptionRes),
            style = ComposeAppTheme.typography.body,
            color = ComposeAppTheme.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        InputField(
            value = uiState.amount,
            onValueChange = onValueChanged,
            placeholderText = stringResource(Res.string.enter_coin_amount),
            modifier = Modifier.padding(vertical = 6.dp)
        )
        if (uiState.calculateResult.isNotEmpty()) {
            CalculatorTable(uiState = uiState, modifier = Modifier.padding(top = 12.dp))
        }
        Text(
            text = uiState.coinExchange,
            style = ComposeAppTheme.typography.body,
            color = ComposeAppTheme.colors.textPrimary,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        ButtonPrimaryYellow(
            title = stringResource(Res.string.done),
            onClick = onDoneClicked,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp)
        )
    }
}

@Composable
internal fun CalculatorTable(uiState: CalculatorUIState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.border(
            width = 1.dp,
            color = ComposeAppTheme.colors.borderDefault,
            shape = RoundedCornerShape(12.dp)
        )
    ) {
        CalculatorItemHeader(
            period = stringResource(Res.string.period),
            coin = uiState.stackingType.value,
            coinSecondary = uiState.coinSecondary
        )
        uiState.calculateResult.forEach { calculatorItem ->

            val period = when (calculatorItem.period) {
                PeriodType.DAY -> stringResource(Res.string.day_period)
                PeriodType.WEEK -> stringResource(Res.string.week_period)
                PeriodType.MONTH -> stringResource(Res.string.month_period)
                PeriodType.YEAR -> stringResource(Res.string.year_period)
                PeriodType.UNKNOWN -> return@forEach
            }
            HorizontalDivider(
                color = ComposeAppTheme.colors.borderDivider,
                thickness = 1.dp
            )
            CalculatorItemRow(
                period = period,
                coin = calculatorItem.amount,
                coinSecondary = calculatorItem.amountSecondary
            )
        }
    }
}

@Composable
internal fun CalculatorItemHeader(period: String, coin: String, coinSecondary: String) {
    Row(
        modifier = Modifier
            .height(MinCalculatorRowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = period,
            style = ComposeAppTheme.typography.subhead1,
            color = ComposeAppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Spacer(
            modifier = Modifier
                .background(ComposeAppTheme.colors.borderDivider)
                .height(MinCalculatorRowHeight)
                .width(1.dp)
        )
        Text(
            text = coin,
            style = ComposeAppTheme.typography.subhead1,
            color = ComposeAppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Spacer(
            modifier = Modifier
                .background(ComposeAppTheme.colors.borderDivider)
                .height(MinCalculatorRowHeight)
                .width(1.dp)
        )
        Text(
            text = coinSecondary,
            style = ComposeAppTheme.typography.subhead1,
            color = ComposeAppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}


@Composable
internal fun CalculatorItemRow(
    period: String,
    coin: String,
    coinSecondary: String
) {
    Row(
        modifier = Modifier
            .height(MinCalculatorRowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = period,
            style = ComposeAppTheme.typography.captionSB.copy(
                fontWeight = FontWeight.Medium,
            ),
            color = ComposeAppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Spacer(
            modifier = Modifier
                .background(ComposeAppTheme.colors.borderDivider)
                .height(MinCalculatorRowHeight)
                .width(1.dp)
        )
        Text(
            text = coin,
            style = ComposeAppTheme.typography.caption,
            color = ComposeAppTheme.colors.statusSuccess,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Spacer(
            modifier = Modifier
                .background(ComposeAppTheme.colors.borderDivider)
                .height(MinCalculatorRowHeight)
                .width(1.dp)
        )
        Text(
            text = coinSecondary,
            style = ComposeAppTheme.typography.caption,
            color = ComposeAppTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}
