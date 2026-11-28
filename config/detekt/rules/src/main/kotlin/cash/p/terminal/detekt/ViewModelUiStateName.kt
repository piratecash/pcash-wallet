package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.api.Config
import org.jetbrains.kotlin.psi.KtClass

class ViewModelUiStateName(config: Config = Config.empty) :
    MessageRule(config, "A ViewModelUiState implementation must be named after its ViewModel.") {

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)
        if (VIEW_MODEL_UI_STATE in klass.superTypeShortNames() && klass.name?.endsWith("ViewModel") != true) {
            flag(klass, "`${klass.name}` implements $VIEW_MODEL_UI_STATE, so its name must end with `ViewModel`.")
        }
    }
}
