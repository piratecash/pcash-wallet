package cash.p.terminal.modules.send.address

import cash.p.terminal.resources.NotAvailable
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Send_Address_Error_Clear
import cash.p.terminal.resources.Send_Address_Error_Detected
import cash.p.terminal.resources.alpha_aml_high_risk
import cash.p.terminal.resources.alpha_aml_low_risk
import cash.p.terminal.resources.alpha_aml_very_high_risk
import cash.p.terminal.resources.alpha_aml_very_low_risk
import org.jetbrains.compose.resources.StringResource

enum class AddressCheckResult {
    Clear,
    Detected,
    NotAvailable,
    NotAllowed,
    NotSupported,
    AlphaAmlVeryLow,
    AlphaAmlLow,
    AlphaAmlHigh,
    AlphaAmlVeryHigh;

    val title: StringResource
        get() = when (this) {
            Clear -> Res.string.Send_Address_Error_Clear
            Detected -> Res.string.Send_Address_Error_Detected
            AlphaAmlVeryLow -> Res.string.alpha_aml_very_low_risk
            AlphaAmlLow -> Res.string.alpha_aml_low_risk
            AlphaAmlHigh -> Res.string.alpha_aml_high_risk
            AlphaAmlVeryHigh -> Res.string.alpha_aml_very_high_risk
            NotAvailable,
            NotAllowed,
            NotSupported -> Res.string.NotAvailable
        }
}
