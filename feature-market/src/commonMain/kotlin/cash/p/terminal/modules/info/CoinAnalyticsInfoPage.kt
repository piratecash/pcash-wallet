package cash.p.terminal.modules.info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.CoinAnalytics_ActiveAddresses_Info1
import cash.p.terminal.resources.CoinAnalytics_ActiveAddresses_Info2
import cash.p.terminal.resources.CoinAnalytics_ActiveAddresses_Info3
import cash.p.terminal.resources.CoinAnalytics_ActiveAddresses_Info4
import cash.p.terminal.resources.CoinAnalytics_ActiveAddresses_Info5
import cash.p.terminal.resources.CoinAnalytics_CexVolume_Info1
import cash.p.terminal.resources.CoinAnalytics_CexVolume_Info2
import cash.p.terminal.resources.CoinAnalytics_CexVolume_Info3
import cash.p.terminal.resources.CoinAnalytics_CexVolume_Info4
import cash.p.terminal.resources.CoinAnalytics_DexLiquidity_Info1
import cash.p.terminal.resources.CoinAnalytics_DexLiquidity_Info2
import cash.p.terminal.resources.CoinAnalytics_DexLiquidity_Info3
import cash.p.terminal.resources.CoinAnalytics_DexLiquidity_TrackedDexes
import cash.p.terminal.resources.CoinAnalytics_DexLiquidity_TrackedDexes_Info1
import cash.p.terminal.resources.CoinAnalytics_DexLiquidity_TrackedDexes_Info2
import cash.p.terminal.resources.CoinAnalytics_DexVolume_Info1
import cash.p.terminal.resources.CoinAnalytics_DexVolume_Info2
import cash.p.terminal.resources.CoinAnalytics_DexVolume_Info3
import cash.p.terminal.resources.CoinAnalytics_DexVolume_Info4
import cash.p.terminal.resources.CoinAnalytics_DexVolume_TrackedDexes
import cash.p.terminal.resources.CoinAnalytics_DexVolume_TrackedDexes_Info1
import cash.p.terminal.resources.CoinAnalytics_DexVolume_TrackedDexes_Info2
import cash.p.terminal.resources.CoinAnalytics_Holders_Info1
import cash.p.terminal.resources.CoinAnalytics_Holders_Info2
import cash.p.terminal.resources.CoinAnalytics_Holders_TrackedBlockchains
import cash.p.terminal.resources.CoinAnalytics_ProjectTVL_Info1
import cash.p.terminal.resources.CoinAnalytics_ProjectTVL_Info2
import cash.p.terminal.resources.CoinAnalytics_ProjectTVL_Info3
import cash.p.terminal.resources.CoinAnalytics_ProjectTVL_Info4
import cash.p.terminal.resources.CoinAnalytics_ProjectTVL_Info5
import cash.p.terminal.resources.CoinAnalytics_TransactionCount_Info1
import cash.p.terminal.resources.CoinAnalytics_TransactionCount_Info2
import cash.p.terminal.resources.CoinAnalytics_TransactionCount_Info3
import cash.p.terminal.resources.CoinAnalytics_TransactionCount_Info4
import cash.p.terminal.resources.CoinAnalytics_TransactionCount_Info5
import cash.p.terminal.resources.Error
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.TechnicalAdvice_InfoDescription
import cash.p.terminal.resources.ic_error_48
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.modules.coin.analytics.CoinAnalyticsModule.AnalyticInfo
import cash.p.terminal.modules.info.ui.BulletedText
import cash.p.terminal.modules.info.ui.InfoBody
import cash.p.terminal.modules.info.ui.InfoHeader
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui.compose.components.ScreenMessageWithAction
import cash.p.terminal.ui_compose.theme.ComposeAppTheme

class CoinAnalyticsInfoPage(val input: AnalyticInfo?) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        CoinAnalyticsInfoScreen(input, navigation::navigateUpSafely)
    }
}

@Composable
internal fun CoinAnalyticsInfoScreen(
    analyticsInfo: AnalyticInfo?,
    onBackPress: () -> Unit
) {

    Surface(color = ComposeAppTheme.colors.backgroundBase) {
        Column {
            AppBar(
                navigationIcon = {
                    HsBackButton(onClick = onBackPress)
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                analyticsInfo?.let { info ->
                    InfoHeader(info.title)
                    AnalyticsInfoBody(info)
                    Spacer(Modifier.height(20.dp))
                } ?: run {
                    ScreenMessageWithAction(
                        text = stringResource(Res.string.Error),
                        icon = Res.drawable.ic_error_48
                    ) {
                        ButtonPrimaryYellow(
                            modifier = Modifier
                                .padding(horizontal = 48.dp)
                                .fillMaxWidth(),
                            title = stringResource(Res.string.Button_Close),
                            onClick = onBackPress
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsInfoBody(info: AnalyticInfo) {
    when (info) {
        AnalyticInfo.CexVolumeInfo -> {
            BulletedText(Res.string.CoinAnalytics_CexVolume_Info1)
            BulletedText(Res.string.CoinAnalytics_CexVolume_Info2)
            BulletedText(Res.string.CoinAnalytics_CexVolume_Info3)
            BulletedText(Res.string.CoinAnalytics_CexVolume_Info4)
        }

        AnalyticInfo.DexVolumeInfo -> {
            BulletedText(Res.string.CoinAnalytics_DexVolume_Info1)
            BulletedText(Res.string.CoinAnalytics_DexVolume_Info2)
            BulletedText(Res.string.CoinAnalytics_DexVolume_Info3)
            BulletedText(Res.string.CoinAnalytics_DexVolume_Info4)
            InfoBody(Res.string.CoinAnalytics_DexVolume_TrackedDexes)
            BulletedText(Res.string.CoinAnalytics_DexVolume_TrackedDexes_Info1)
            BulletedText(Res.string.CoinAnalytics_DexVolume_TrackedDexes_Info2)
        }

        AnalyticInfo.DexLiquidityInfo -> {
            BulletedText(Res.string.CoinAnalytics_DexLiquidity_Info1)
            BulletedText(Res.string.CoinAnalytics_DexLiquidity_Info2)
            BulletedText(Res.string.CoinAnalytics_DexLiquidity_Info3)
            InfoBody(Res.string.CoinAnalytics_DexLiquidity_TrackedDexes)
            BulletedText(Res.string.CoinAnalytics_DexLiquidity_TrackedDexes_Info1)
            BulletedText(Res.string.CoinAnalytics_DexLiquidity_TrackedDexes_Info2)
        }

        AnalyticInfo.AddressesInfo -> {
            BulletedText(Res.string.CoinAnalytics_ActiveAddresses_Info1)
            BulletedText(Res.string.CoinAnalytics_ActiveAddresses_Info2)
            BulletedText(Res.string.CoinAnalytics_ActiveAddresses_Info3)
            BulletedText(Res.string.CoinAnalytics_ActiveAddresses_Info4)
            BulletedText(Res.string.CoinAnalytics_ActiveAddresses_Info5)
        }

        AnalyticInfo.TransactionCountInfo -> {
            BulletedText(Res.string.CoinAnalytics_TransactionCount_Info1)
            BulletedText(Res.string.CoinAnalytics_TransactionCount_Info2)
            BulletedText(Res.string.CoinAnalytics_TransactionCount_Info3)
            BulletedText(Res.string.CoinAnalytics_TransactionCount_Info4)
            BulletedText(Res.string.CoinAnalytics_TransactionCount_Info5)
        }

        AnalyticInfo.HoldersInfo -> {
            BulletedText(Res.string.CoinAnalytics_Holders_Info1)
            BulletedText(Res.string.CoinAnalytics_Holders_Info2)
            InfoBody(Res.string.CoinAnalytics_Holders_TrackedBlockchains)
        }

        AnalyticInfo.TvlInfo -> {
            BulletedText(Res.string.CoinAnalytics_ProjectTVL_Info1)
            BulletedText(Res.string.CoinAnalytics_ProjectTVL_Info2)
            BulletedText(Res.string.CoinAnalytics_ProjectTVL_Info3)
            BulletedText(Res.string.CoinAnalytics_ProjectTVL_Info4)
            BulletedText(Res.string.CoinAnalytics_ProjectTVL_Info5)
        }

        AnalyticInfo.TechnicalIndicatorsInfo -> {
            InfoBody(Res.string.TechnicalAdvice_InfoDescription)
        }
    }
}
