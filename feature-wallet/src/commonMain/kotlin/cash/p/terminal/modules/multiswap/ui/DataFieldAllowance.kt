package cash.p.terminal.modules.multiswap.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.entities.CoinValue
import cash.p.terminal.modules.fee.QuoteInfoRow
import cash.p.terminal.ui_compose.components.InfoBottomSheet
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.subhead2_lucian
import cash.p.terminal.wallet.Token
import java.math.BigDecimal
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SwapInfo_AllowanceDescription
import cash.p.terminal.resources.SwapInfo_AllowanceTitle
import cash.p.terminal.resources.Swap_Allowance
import cash.p.terminal.resources.ic_info_20

data class DataFieldAllowance(val allowance: BigDecimal, val token: Token) : DataField {
    @Composable
    override fun GetContent(navigation: HSNavigation, borderTop: Boolean) {
        val infoTitle = stringResource(Res.string.SwapInfo_AllowanceTitle)
        val infoText = stringResource(Res.string.SwapInfo_AllowanceDescription)
        var showInfoDialog by remember { mutableStateOf(false) }

        QuoteInfoRow(
            borderTop = borderTop,
            title = {
                subhead2_grey(text = stringResource(Res.string.Swap_Allowance))

                Image(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clickable(
                            onClick = { showInfoDialog = true },
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        )
                    ,
                    painter = painterResource(Res.drawable.ic_info_20),
                    contentDescription = ""
                )
            },
            value = {
                subhead2_lucian(text = CoinValue(token, allowance).getFormattedFull())
            }
        )

        if (showInfoDialog) {
            InfoBottomSheet(
                title = infoTitle,
                text = infoText,
                onDismiss = { showInfoDialog = false }
            )
        }
    }
}
