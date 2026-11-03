package cash.p.terminal.modules.sendevmtransaction

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_arrow_down_left_24
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType

@Preview
@Composable
private fun SubheadPreview() {
    val item = ViewItem.Subhead("Title", "Value", Res.drawable.ic_arrow_down_left_24)
    ComposeAppTheme {
        Subhead(item)
    }
}

@Preview
@Composable
private fun TitleValuePreview() {
    val item = ViewItem.Value("Title", "Value", ValueType.Incoming)
    ComposeAppTheme {
        TitleValue(item)
    }
}

@Preview
@Composable
private fun AmountMultiPreview() {
    val token = Token(
        coin = Coin("uid", "KuCoin", "KCS"),
        blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
        type = TokenType.Eip20("eef"),
        decimals = 18
    )
    val item = ViewItem.AmountMulti(
        listOf(
            AmountValues("0.104 KCS (est)", "$0.99"),
            AmountValues("0.103 KCS (min)", "$0.95"),
        ),
        ValueType.Incoming,
        token
    )
    ComposeAppTheme {
        AmountMulti(item)
    }
}

@Preview
@Composable
private fun AmountPreview() {
    val token = Token(
        coin = Coin("uid", "KuCoin", "KCS"),
        blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
        type = TokenType.Eip20("eef"),
        decimals = 18
    )
    val item = ViewItem.Amount(
        "$0.99",
        "0.104 KCS (est)",
        ValueType.Outgoing,
        token
    )
    ComposeAppTheme {
        Amount(item)
    }
}

@Preview
@Composable
private fun TitleValueHexPreview() {
    ComposeAppTheme {
        TitleValueHex("Title", "ValueShort", "ValueLong")
    }
}
