package cash.p.terminal.modules.transactions

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.aml_high_risk
import cash.p.terminal.resources.aml_low_risk
import cash.p.terminal.resources.aml_medium_risk
import cash.p.terminal.resources.aml_unknown
import org.jetbrains.compose.resources.StringResource


enum class IncomingAddressCheckResult {
    Unknown,
    Low,
    Medium,
    High;

    val title: StringResource
        get() = when (this) {
            Unknown -> Res.string.aml_unknown
            Low -> Res.string.aml_low_risk
            Medium -> Res.string.aml_medium_risk
            High -> Res.string.aml_high_risk
        }
}
