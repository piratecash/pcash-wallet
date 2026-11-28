package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.api.Config
import org.jetbrains.kotlin.psi.KtClass

class ViewModelContextParameter(config: Config = Config.empty) :
    MessageRule(config, "ViewModels must not receive Context in the constructor.") {

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)
        if (!klass.isViewModel()) return
        klass.primaryConstructorParameters
            .filter { it.typeReference?.shortName() == "Context" }
            .forEach { flag(it, "Do not inject `Context` into a ViewModel; use `App.instance` if unavoidable.") }
    }

    private fun KtClass.isViewModel() =
        superTypeShortNames().any { it.endsWith("ViewModel") || it == VIEW_MODEL_UI_STATE }
}
