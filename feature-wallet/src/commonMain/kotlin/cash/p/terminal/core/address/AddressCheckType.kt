package cash.p.terminal.core.address

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Send_Address_BlacklistCheck
import cash.p.terminal.resources.Send_Address_ErrorMessage_AmlDetected
import cash.p.terminal.resources.Send_Address_ErrorMessage_AmlDetected_Description
import cash.p.terminal.resources.Send_Address_ErrorMessage_BlacklistDetected
import cash.p.terminal.resources.Send_Address_ErrorMessage_BlacklistDetected_Description
import cash.p.terminal.resources.Send_Address_ErrorMessage_PhishingDetected
import cash.p.terminal.resources.Send_Address_ErrorMessage_PhishingDetected_Description
import cash.p.terminal.resources.Send_Address_ErrorMessage_SanctionDetected
import cash.p.terminal.resources.Send_Address_ErrorMessage_SanctionDetected_Description
import cash.p.terminal.resources.Send_Address_ErrorMessage_SmartContractDetected
import cash.p.terminal.resources.Send_Address_ErrorMessage_SmartContractDetected_Description
import cash.p.terminal.resources.Send_Address_NotSmartContractCheck
import cash.p.terminal.resources.Send_Address_PhishingCheck
import cash.p.terminal.resources.Send_Address_SanctionCheck
import cash.p.terminal.resources.check_from_aml
import org.jetbrains.compose.resources.StringResource

enum class AddressCheckType {
    SmartContract,
    Phishing,
    Blacklist,
    AmlCheck,
    Sanction;

    val title: StringResource
        get() = when (this) {
            SmartContract -> Res.string.Send_Address_NotSmartContractCheck
            Phishing -> Res.string.Send_Address_PhishingCheck
            Blacklist -> Res.string.Send_Address_BlacklistCheck
            AmlCheck -> Res.string.check_from_aml
            Sanction -> Res.string.Send_Address_SanctionCheck
        }

    val detectedErrorTitle: StringResource
        get() = when (this) {
            SmartContract -> Res.string.Send_Address_ErrorMessage_SmartContractDetected
            Phishing -> Res.string.Send_Address_ErrorMessage_PhishingDetected
            Blacklist -> Res.string.Send_Address_ErrorMessage_BlacklistDetected
            AmlCheck -> Res.string.Send_Address_ErrorMessage_AmlDetected
            Sanction -> Res.string.Send_Address_ErrorMessage_SanctionDetected
        }

    val detectedErrorDescription: StringResource
        get() = when (this) {
            SmartContract -> Res.string.Send_Address_ErrorMessage_SmartContractDetected_Description
            Phishing -> Res.string.Send_Address_ErrorMessage_PhishingDetected_Description
            Blacklist -> Res.string.Send_Address_ErrorMessage_BlacklistDetected_Description
            AmlCheck -> Res.string.Send_Address_ErrorMessage_AmlDetected_Description
            Sanction -> Res.string.Send_Address_ErrorMessage_SanctionDetected_Description
        }

    fun isPremiumRequired(): Boolean {
        return this == SmartContract
    }

}
