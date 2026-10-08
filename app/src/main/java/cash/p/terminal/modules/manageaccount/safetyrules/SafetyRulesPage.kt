package cash.p.terminal.modules.manageaccount.safetyrules

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cash.p.terminal.R
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.navigation.navigateUpSafely

class SafetyRulesPage(val input: SafetyRulesModule.Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val termTitles = listOf(
            stringResource(R.string.safety_rules_checkbox_1),
            stringResource(R.string.safety_rules_checkbox_2),
            stringResource(R.string.safety_rules_checkbox_3)
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

    @Parcelize
    enum class Result : Parcelable {
        AGREED,
        RISK_IT,
        CANCELLED
    }
}
