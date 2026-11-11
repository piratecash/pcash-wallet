package cash.p.terminal.modules.pin

import androidx.compose.runtime.Composable
import cash.p.terminal.core.rememberEnsurePinSet
import cash.p.terminal.modules.pin.hiddenwallet.SetHiddenWalletPinPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.resources.AdvancedSecurity_Terms_Checkboxes
import cash.p.terminal.resources.AdvancedSecurity_Terms_Warning
import cash.p.terminal.resources.DuressPin_Description
import cash.p.terminal.resources.DuressPin_Notes_Biometrics_Description
import cash.p.terminal.resources.DuressPin_Notes_Biometrics_Title
import cash.p.terminal.resources.DuressPin_Notes_PasscodeChange_Description
import cash.p.terminal.resources.DuressPin_Notes_PasscodeChange_Title
import cash.p.terminal.resources.DuressPin_Notes_PasscodeDisabling_Description
import cash.p.terminal.resources.DuressPin_Notes_PasscodeDisabling_Title
import cash.p.terminal.resources.PinSet_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_edit_24
import cash.p.terminal.resources.ic_passcode
import cash.p.terminal.resources.icon_touch_id_24
import io.horizontalsystems.core.ISystemInfoManager

class AndroidDuressSecretPages(private val systemInfoManager: ISystemInfoManager) : DuressSecretPages {

    override val wording: DuressWording
        get() = DuressWording(
            introDescription = Res.string.DuressPin_Description,
            introNotes = listOfNotNull(
                DuressNote(
                    Res.drawable.icon_touch_id_24,
                    Res.string.DuressPin_Notes_Biometrics_Title,
                    Res.string.DuressPin_Notes_Biometrics_Description,
                ).takeIf { systemInfoManager.biometricAuthSupported },
                DuressNote(
                    Res.drawable.ic_passcode,
                    Res.string.DuressPin_Notes_PasscodeDisabling_Title,
                    Res.string.DuressPin_Notes_PasscodeDisabling_Description,
                ),
                DuressNote(
                    Res.drawable.ic_edit_24,
                    Res.string.DuressPin_Notes_PasscodeChange_Title,
                    Res.string.DuressPin_Notes_PasscodeChange_Description,
                ),
            ),
            termsWarning = Res.string.AdvancedSecurity_Terms_Warning,
            termsCheckboxes = Res.array.AdvancedSecurity_Terms_Checkboxes,
        )

    override fun setDuress(accountIds: List<String>?): HSPage =
        SetDuressPinPage(accountIds?.let(SetDuressPinPage::Input))

    @Composable
    override fun rememberOpenHiddenWalletSecret(navigation: HSNavigation): () -> Unit =
        navigation.rememberEnsurePinSet(Res.string.PinSet_Title, PinType.REGULAR) {
            navigation.slideFromRight(SetHiddenWalletPinPage())
        }
}
