package cash.p.terminal.modules.settings.terms

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SettingsTerms_Backup
import cash.p.terminal.resources.SettingsTerms_Bugs
import cash.p.terminal.resources.SettingsTerms_DisablingPin
import cash.p.terminal.resources.SettingsTerms_JailBraking
import cash.p.terminal.resources.SettingsTerms_PrivateKeys
import org.jetbrains.compose.resources.StringResource

object TermsModule {

    enum class TermType(val key: String) {
        Backup("term_backup"),
        PrivateKeys("term_privatekeys"),
        DisablingPin("term_disablingpin"),
        JailBraking("term_jailbraking"),
        Bugs("term_bugs");

        val description: StringResource
            get() = when (this) {
                Backup -> Res.string.SettingsTerms_Backup
                PrivateKeys -> Res.string.SettingsTerms_PrivateKeys
                DisablingPin -> Res.string.SettingsTerms_DisablingPin
                JailBraking -> Res.string.SettingsTerms_JailBraking
                Bugs -> Res.string.SettingsTerms_Bugs
            }
    }

    data class TermViewItem(val termType: TermType, val checked: Boolean)
}
