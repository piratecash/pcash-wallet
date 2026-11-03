package cash.p.terminal.modules.sendevmtransaction

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.core.ethereum.CautionViewItem
import cash.p.terminal.modules.evmfee.Cautions
import cash.p.terminal.modules.multiswap.ui.DataField
import cash.p.terminal.modules.fee.DataFieldFee
import cash.p.terminal.modules.send.SendModule
import cash.p.terminal.modules.send.evm.settings.SendEvmNonceViewModel
import cash.p.terminal.strings.helpers.shorten
import cash.p.terminal.ui_compose.components.ButtonSecondaryDefault
import cash.p.terminal.ui.compose.components.CoinImage
import cash.p.terminal.ui.compose.components.TransactionInfoAddressCell
import cash.p.terminal.ui.compose.components.TransactionInfoContactCell
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HFillSpacer
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.NftIcon
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.components.headline2_leah
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import io.horizontalsystems.core.Translator
import cash.p.terminal.wallet.Token
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.CoinPlatforms_Native
import cash.p.terminal.resources.Hud_Text_Copied
import cash.p.terminal.resources.Send_Confirmation_Nonce

@Composable
fun SendEvmTransactionView(
    navigation: HSNavigation,
    items: List<SectionViewItem>,
    cautions: List<CautionViewItem>,
    transactionFields: List<DataField>,
    networkFee: SendModule.AmountData?,
) {
    Column {
        items.forEach { sectionViewItem ->
            SectionView(sectionViewItem.viewItems, navigation)
            Spacer(Modifier.height(16.dp))
        }

        if (transactionFields.isNotEmpty()) {
            VSpacer(height = 16.dp)
            SectionUniversalLawrence {
                transactionFields.forEachIndexed { index, field ->
                    field.GetContent(navigation, index != 0)
                }
            }
        }

        VSpacer(height = 16.dp)
        SectionUniversalLawrence {
            DataFieldFee(
                networkFee?.primary?.getFormattedPlain() ?: "---",
                networkFee?.secondary?.getFormattedPlain() ?: "---"
            )
        }

        if (cautions.isNotEmpty()) {
            Cautions(cautions)
        }
    }
}

@Composable
private fun NonceView(nonceViewModel: SendEvmNonceViewModel) {
    val uiState = nonceViewModel.uiState
    if (!uiState.showInConfirmation) return
    val nonce = uiState.nonce ?: return

    Spacer(Modifier.height(16.dp))
    CellUniversalLawrenceSection(
        listOf {
            RowUniversal(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                subhead2_grey(
                    text = stringResource(Res.string.Send_Confirmation_Nonce)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = nonce.toString(),
                    maxLines = 1,
                    style = ComposeAppTheme.typography.subhead1,
                    color = setColorByType(ValueType.Regular)
                )
            }
        }
    )
}

@Composable
fun SectionView(viewItems: List<ViewItem>, navigation: HSNavigation) {
    CellUniversalLawrenceSection(viewItems) { item ->
        when (item) {
            is ViewItem.Subhead -> Subhead(item)
            is ViewItem.Value -> TitleValue(item)
            is ViewItem.ValueMulti -> TitleValueMulti(item)
            is ViewItem.AmountMulti -> AmountMulti(item)
            is ViewItem.Amount -> Amount(item)
            is ViewItem.AmountWithTitle -> AmountWithTitle(item)
            is ViewItem.NftAmount -> NftAmount(item)
            is ViewItem.Address -> {
                TransactionInfoAddressCell(
                    title = item.title,
                    value = item.value,
                    showAdd = item.showAdd,
                    blockchainType = item.blockchainType,
                    navigation = navigation,
                    onCopy = {
                    },
                    onAddToExisting = {
                    },
                    onAddToNew = {
                    }
                )
            }
            is ViewItem.ContactItem -> TransactionInfoContactCell(item.contact.name)
            is ViewItem.Input -> TitleValueHex(item.title, item.value.shorten(), item.value)
            is ViewItem.TokenItem -> Token(item)
            is ViewItem.Fee -> DataFieldFee(
                item.networkFee.primary.getFormattedPlain() ?: "---",
                item.networkFee.secondary?.getFormattedPlain() ?: "---"
            )
        }
    }
}

@Composable
internal fun Subhead(item: ViewItem.Subhead) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        item.iconRes?.let {
            Icon(
                modifier = Modifier.padding(end = 16.dp),
                painter = painterResource(it),
                contentDescription = null,
                tint = ComposeAppTheme.colors.iconSecondary
            )
        }
        headline2_leah(
            text = item.title
        )
        Spacer(Modifier.weight(1f))
        subhead1_grey(
            text = item.value
        )
    }
}

@Composable
fun TitleValue(item: ViewItem.Value) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        subhead2_grey(
            text = item.title
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = item.value,
            maxLines = 1,
            style = ComposeAppTheme.typography.subhead1,
            color = setColorByType(item.type)
        )
    }
}

@Composable
private fun TitleValueMulti(item: ViewItem.ValueMulti) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        subhead2_grey(
            text = item.title
        )
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = item.primaryValue,
                maxLines = 1,
                style = ComposeAppTheme.typography.subhead1,
                color = setColorByType(item.type)
            )
            Text(
                text = item.secondaryValue,
                maxLines = 1,
                style = ComposeAppTheme.typography.caption,
                color = ComposeAppTheme.colors.textSecondary
            )
        }
    }
}

@Composable
internal fun AmountMulti(item: ViewItem.AmountMulti) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        CoinImage(
            token = item.token,
            modifier = Modifier.size(32.dp)
        )
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.amounts[0].coinAmount,
                    maxLines = 1,
                    style = ComposeAppTheme.typography.subhead1,
                    color = setColorByType(item.type)
                )
                Spacer(Modifier.weight(1f))
                subhead2_grey(
                    text = item.amounts[0].fiatAmount ?: ""
                )
            }
            if (item.amounts.size > 1) {
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    caption_grey(
                        text = item.amounts[1].coinAmount
                    )
                    Spacer(Modifier.weight(1f))
                    caption_grey(
                        text = item.amounts[1].fiatAmount ?: ""
                    )
                }
            }
        }
    }
}

@Composable
internal fun Amount(item: ViewItem.Amount) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        CoinImage(
            token = item.token,
            modifier = Modifier
                .padding(end = 16.dp)
                .size(32.dp)
        )
        Text(
            text = item.coinAmount,
            maxLines = 1,
            style = ComposeAppTheme.typography.subhead1,
            color = setColorByType(item.type)
        )
        Spacer(Modifier.weight(1f))
        subhead2_grey(
            text = item.fiatAmount ?: ""
        )
    }
}

@Composable
private fun AmountWithTitle(item: ViewItem.AmountWithTitle) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        CoinImage(
            token = item.token,
            modifier = Modifier.size(32.dp)
        )
        HSpacer(16.dp)
        Column {
            subhead2_leah(text = item.title)
            VSpacer(height = 1.dp)
            caption_grey(text = item.badge ?: stringResource(Res.string.CoinPlatforms_Native))
        }
        HFillSpacer(minWidth = 8.dp)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = item.coinAmount,
                maxLines = 1,
                style = ComposeAppTheme.typography.subhead1,
                color = setColorByType(item.type)
            )
            item.fiatAmount?.let {
                VSpacer(height = 1.dp)
                subhead2_grey(text = it)
            }
        }
    }
}

@Composable
private fun NftAmount(item: ViewItem.NftAmount) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        NftIcon(
            modifier = Modifier.padding(end = 16.dp),
            iconUrl = item.iconUrl,
        )
        Text(
            text = item.amount,
            maxLines = 1,
            style = ComposeAppTheme.typography.subhead2,
            color = setColorByType(item.type)
        )
    }
}

@Composable
internal fun Token(item: ViewItem.TokenItem) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        CoinImage(
            token = item.token,
            modifier = Modifier
                .padding(end = 16.dp)
                .size(32.dp)
        )
        subhead1_leah(item.token.coin.code)
    }
}

@Composable
internal fun TitleValueHex(
    title: String,
    valueTitle: String,
    value: String,
) {
    val hud = rememberHudMessenger()
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        subhead2_grey(
            text = title
        )
        Spacer(Modifier.weight(1f))
        ButtonSecondaryDefault(
            modifier = Modifier.height(28.dp),
            title = valueTitle,
            onClick = {
                getKoinInstance<IClipboardManager>().copyText(value)
                hud.showSuccess(Translator.getString(Res.string.Hud_Text_Copied))
            }
        )
    }
}

@Composable
private fun setColorByType(type: ValueType) =
    when (type) {
        ValueType.Regular -> ComposeAppTheme.colors.textPrimary
        ValueType.Disabled -> ComposeAppTheme.colors.textSecondary
        ValueType.Outgoing -> ComposeAppTheme.colors.textPrimary
        ValueType.Incoming -> ComposeAppTheme.colors.statusSuccess
        ValueType.Warning -> ComposeAppTheme.colors.statusWarning
        ValueType.Forbidden -> ComposeAppTheme.colors.statusError
    }
