package cash.p.terminal.modules.manageaccount.safetyrules

import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize

object SafetyRulesModule {

    @CommonParcelize
    data class Input(
        val mode: SafetyRulesMode
    ) : CommonParcelable

    @CommonParcelize
    enum class SafetyRulesMode : CommonParcelable {
        AGREE,        // First time - checkboxes unchecked, "I Agree" button
        COPY_CONFIRM  // Copy confirmation - checkboxes pre-checked, "Risk It" + "Don't Copy" buttons
    }
}
