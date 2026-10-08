package cash.p.terminal.featureStacking.ui.staking

import androidx.compose.runtime.Composable
import cash.p.terminal.featureStacking.ui.calculatorScreen.CalculatorViewModel
import cash.p.terminal.navigation.AppPages
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.wallet.navigation.WalletPages
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import java.math.BigDecimal

class StackingPage(val input: StackingType?) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<StackingViewModel> { parametersOf(input) }
        val calculatorViewModel = koinViewModel<CalculatorViewModel>()
        val appPages: AppPages = koinInject()
        val walletPages: WalletPages = koinInject()

        if (calculatorViewModel.uiState.value.calculateResult.isEmpty()) {
            calculatorViewModel.setCalculatorValue("10000")
        }

        fun setCalculatorData(stackingType: StackingType, balance: BigDecimal) {
            val minBalance = BigDecimal(stackingType.minStackingAmount)
            val defaultValue = if (stackingType == StackingType.PCASH) "10000" else "1000"

            val value = if (balance < minBalance) {
                defaultValue
            } else {
                balance.toPlainString()
            }
            calculatorViewModel.setCalculatorValue(value)
            calculatorViewModel.setCoin(stackingType)
            viewModel.setStackingType(stackingType)
        }

        StackingScreen(
            uiState = viewModel.uiState.value,
            calculatorUIState = calculatorViewModel.uiState.value,
            onCalculatorValueChanged = calculatorViewModel::setCalculatorValue,
            onTabChanged = viewModel::setStackingType,
            onBuyClicked = { token ->
                navigation.slideFromRight(walletPages.swap(tokenOut = token))
            },
            onChartClicked = { coinUid ->
                navigation.slideFromRight(appPages.coin(coinUid))
            },
            onClickClose = { navigation.navigateUpSafely() },
            setCalculatorData = ::setCalculatorData
        )
    }
}
