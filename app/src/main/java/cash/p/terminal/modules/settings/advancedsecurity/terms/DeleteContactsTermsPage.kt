package cash.p.terminal.modules.settings.advancedsecurity.terms

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringArrayResource
import cash.p.terminal.R
import cash.p.terminal.core.ensurePinSet
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.pin.SetPinPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.components.HudHelper
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely

class DeleteContactsTermsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val view = LocalView.current
        val termTitles = stringArrayResource(R.array.delete_all_contacts_terms_checkboxes)
        val viewModel: DeleteContactsTermsViewModel = koinViewModel {
            parametersOf(termTitles)
        }

        DeleteContactsTermsScreen(
            uiState = viewModel.uiState,
            onCheckboxToggle = viewModel::toggleCheckbox,
            onAgreeClick = {
                navigation.ensurePinSet(R.string.PinSet_Info) {
                    navigation.slideFromRightForResult<SetPinPage.Result>(
                        SetPinPage(
                            SetPinPage.Input(
                                descriptionResId = R.string.pin_set_for_delete_all_contacts,
                                pinType = PinType.DELETE_CONTACTS
                            )
                        )
                    ) {
                        HudHelper.showSuccessMessage(view, R.string.Hud_Text_Created)
                        navigation.navigateUpFrom(this@DeleteContactsTermsPage)
                    }
                }
            },
            onNavigateBack = navigation::navigateUpSafely
        )
    }
}
