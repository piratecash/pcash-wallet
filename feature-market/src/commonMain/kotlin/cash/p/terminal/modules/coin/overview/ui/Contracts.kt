package cash.p.terminal.modules.coin.overview.ui

import cash.p.terminal.resources.Button_Browser
import cash.p.terminal.resources.Button_Copy
import cash.p.terminal.resources.CoinPage_Contracts
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_copy_20
import cash.p.terminal.resources.ic_globe_20
import cash.p.terminal.resources.ic_platform_placeholder_32
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.coin.ContractInfo
import cash.p.terminal.ui_compose.components.ButtonSecondaryCircle
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.components.CellSingleLineClear
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.RowUniversal
import io.horizontalsystems.chartview.rememberAsyncImagePainterWithFallback

@Composable
fun Contracts(
    contracts: List<ContractInfo>,
    onClickCopy: (ContractInfo) -> Unit,
    onClickExplorer: (String) -> Unit,
) {
    Column {
        CellSingleLineClear(borderTop = true) {
            body_leah(text = stringResource(Res.string.CoinPage_Contracts))
        }

        CellUniversalLawrenceSection(contracts) { contractInfo ->
            RowUniversal(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Image(
                    modifier = Modifier.size(32.dp),
                    painter = rememberAsyncImagePainterWithFallback(
                        model = contractInfo.imgUrl,
                        error = painterResource(Res.drawable.ic_platform_placeholder_32)
                    ),
                    contentDescription = "platform"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    contractInfo.name?.let {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            body_leah(
                                modifier = Modifier.weight(1f, fill = false),
                                text = it,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            contractInfo.schema?.let { labelText ->
                                Box(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ComposeAppTheme.colors.badgeBackground)
                                ) {
                                    Text(
                                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 1.dp),
                                        text = labelText,
                                        color = ComposeAppTheme.colors.textPrimary,
                                        style = ComposeAppTheme.typography.microSB,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(1.dp))
                    }
                    subhead2_grey(
                        text = contractInfo.shortened,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ButtonSecondaryCircle(
                    icon = Res.drawable.ic_copy_20,
                    contentDescription = stringResource(Res.string.Button_Copy),
                    onClick = {
                        onClickCopy.invoke(contractInfo)
                    }
                )
                contractInfo.explorerUrl?.let { explorerUrl ->
                    ButtonSecondaryCircle(
                        modifier = Modifier.padding(start = 16.dp),
                        icon = Res.drawable.ic_globe_20,
                        contentDescription = stringResource(Res.string.Button_Browser),
                        onClick = {
                            onClickExplorer.invoke(explorerUrl)
                        }
                    )
                }
            }
        }
    }
}
