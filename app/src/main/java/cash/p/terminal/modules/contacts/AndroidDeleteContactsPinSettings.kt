package cash.p.terminal.modules.contacts

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.R
import cash.p.terminal.core.Caution
import cash.p.terminal.core.rememberAuthorizedDeleteContactsPasscodeAction
import cash.p.terminal.core.rememberPremiumAction
import cash.p.terminal.core.slideToDeleteContactsTerms
import cash.p.terminal.modules.contacts.screen.ConfirmationBottomSheet
import cash.p.terminal.modules.pin.EditPinPage
import cash.p.terminal.modules.pin.PinType
import cash.p.terminal.modules.pin.SetPinPage
import cash.p.terminal.modules.settings.security.ui.ManagePasscodeSection
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.pin_set_for_delete_all_contacts
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.IPinComponent
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.reactive.asFlow

class AndroidDeleteContactsPinSettings(private val pinComponent: IPinComponent) : DeleteContactsPinSettings {

    // Re-read on every (re)collection: the PIN is edited on pages that stop this one's collector.
    private val pinSetFlow = pinComponent.pinSetFlowable.asFlow()
        .onStart { emit(Unit) }
        .map { pinComponent.isDeleteContactsPinSet() }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Section(navigation: HSNavigation) {
        val pinSet by pinSetFlow.collectAsStateWithLifecycle(pinComponent.isDeleteContactsPinSet())
        var showDisableConfirmation by remember { mutableStateOf(false) }
        val editPin = navigation.rememberAuthorizedDeleteContactsPasscodeAction {
            navigation.slideFromRight(
                EditPinPage(
                    SetPinPage.Input(
                        description = Res.string.pin_set_for_delete_all_contacts,
                        pinType = PinType.DELETE_CONTACTS
                    )
                )
            )
        }
        val openTerms = navigation.rememberPremiumAction(navigation::slideToDeleteContactsTerms)
        val disablePin =
            navigation.rememberAuthorizedDeleteContactsPasscodeAction(pinComponent::disableDeleteContactsPin)

        ManagePasscodeSection(
            iconRes = R.drawable.ic_passcode,
            enabled = pinSet,
            editTextRes = R.string.SettingsSecurity_EditPin,
            onManageClick = { if (pinSet) editPin() else openTerms() },
            onDisableClick = { showDisableConfirmation = true }
        )
        InfoText(text = stringResource(R.string.pin_set_for_delete_all_contacts))

        if (showDisableConfirmation) {
            TransparentModalBottomSheet(
                onDismissRequest = { showDisableConfirmation = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                ConfirmationBottomSheet(
                    title = stringResource(R.string.SettingsSecurity_DisablePin),
                    text = stringResource(R.string.disable_delete_contacts_passcode_warning),
                    iconPainter = painterResource(R.drawable.icon_warning_2_20),
                    iconTint = ColorFilter.tint(ComposeAppTheme.colors.statusWarning),
                    confirmText = stringResource(R.string.SettingsSecurity_DisablePin),
                    cautionType = Caution.Type.Error,
                    cancelText = stringResource(R.string.Button_Cancel),
                    onConfirm = {
                        showDisableConfirmation = false
                        disablePin()
                    },
                    onClose = { showDisableConfirmation = false }
                )
            }
        }
    }
}
