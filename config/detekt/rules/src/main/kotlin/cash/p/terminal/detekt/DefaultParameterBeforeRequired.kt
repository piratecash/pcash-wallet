package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.api.Config
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter

class DefaultParameterBeforeRequired(config: Config = Config.empty) :
    MessageRule(config, "Parameters with defaults must come after all required parameters.") {

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (function.hasModifier(KtTokens.OVERRIDE_KEYWORD)) return
        val isComposable = function.annotationEntries.any { it.shortName?.asString() == "Composable" }
        check(function.valueParameters, contentSlotAllowed = isComposable)
    }

    override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
        super.visitPrimaryConstructor(constructor)
        check(constructor.valueParameters, contentSlotAllowed = false)
    }

    override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor) {
        super.visitSecondaryConstructor(constructor)
        check(constructor.valueParameters, contentSlotAllowed = false)
    }

    private fun check(parameters: List<KtParameter>, contentSlotAllowed: Boolean) {
        val firstDefault = parameters.indexOfFirst { it.hasDefaultValue() }
        if (firstDefault < 0) return
        parameters.withIndex()
            .filter { (index, parameter) ->
                index > firstDefault && !parameter.hasDefaultValue() && !parameter.isVarArg
            }
            .filterNot { (index, parameter) ->
                contentSlotAllowed && index == parameters.lastIndex && parameter.isContentSlot()
            }
            .forEach { (_, parameter) ->
                flag(parameter, "Move `${parameter.name}` before parameters with default values.")
            }
    }

    private fun KtParameter.isContentSlot(): Boolean {
        val type = typeReference ?: return false
        return type.unwrapFunctionType() != null && type.text.contains("@Composable")
    }
}
