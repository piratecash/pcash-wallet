package cash.p.terminal.modules.settings.advancedsecurity.terms

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringArrayResource
import cash.p.terminal.R
import cash.p.terminal.core.rememberEnsurePinSet
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.pin.SetPinPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.components.HudHelper
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.navigation.slideFromRight
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.PinSet_Info
import cash.p.terminal.resources.pin_set_for_delete_all_contacts

class DeleteContactsTermsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val view = LocalView.current
        val termTitles = stringArrayResource(R.array.delete_all_contacts_terms_checkboxes)
        val viewModel: DeleteContactsTermsViewModel = koinViewModel {
            parametersOf(termTitles)
        }
        val deleteContactsPinSet = navigation.rememberResultLauncher<SetPinPage.Result> {
            HudHelper.showSuccessMessage(view, R.string.Hud_Text_Created)
            navigation.navigateUpFrom(this@DeleteContactsTermsPage)
        }
        val openSetDeleteContactsPin = navigation.rememberEnsurePinSet(Res.string.PinSet_Info, PinType.REGULAR) {
            deleteContactsPinSet.slideFromRight(
                SetPinPage(
                    SetPinPage.Input(
                        description = Res.string.pin_set_for_delete_all_contacts,
                        pinType = PinType.DELETE_CONTACTS
                    )
                )
            )
        }

        DeleteContactsTermsScreen(
            uiState = viewModel.uiState,
            onCheckboxToggle = viewModel::toggleCheckbox,
            onAgreeClick = openSetDeleteContactsPin,
            onNavigateBack = navigation::navigateUpSafely
        )
    }
}
