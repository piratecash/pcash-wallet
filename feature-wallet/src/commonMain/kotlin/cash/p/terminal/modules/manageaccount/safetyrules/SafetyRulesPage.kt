package cash.p.terminal.modules.manageaccount.safetyrules

import io.horizontalsystems.core.common.CommonParcelable
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import io.horizontalsystems.core.common.CommonParcelize
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.safety_rules_checkbox_1
import cash.p.terminal.resources.safety_rules_checkbox_2
import cash.p.terminal.resources.safety_rules_checkbox_3

class SafetyRulesPage(val input: SafetyRulesModule.Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val termTitles = listOf(
            stringResource(Res.string.safety_rules_checkbox_1),
            stringResource(Res.string.safety_rules_checkbox_2),
            stringResource(Res.string.safety_rules_checkbox_3)
        )

        val viewModel = koinViewModel<SafetyRulesViewModel> {
            parametersOf(input.mode, termTitles)
        }

        SafetyRulesScreen(
            uiState = viewModel.uiState,
            onCheckboxToggle = viewModel::toggleCheckbox,
            onAgreeClick = {
                viewModel.agree()
                navigation.setResult(this, Result.AGREED)
                navigation.navigateUpSafely()
            },
            onRiskItClick = {
                navigation.setResult(this, Result.RISK_IT)
                navigation.navigateUpSafely()
            },
            onCancelClick = {
                navigation.setResult(this, Result.CANCELLED)
                navigation.navigateUpSafely()
            }
        )
    }

    @CommonParcelize
    enum class Result : CommonParcelable {
        AGREED,
        RISK_IT,
        CANCELLED
    }
}
