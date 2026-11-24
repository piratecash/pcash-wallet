package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.api.Config
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeAlias

class NestedFunctionType(config: Config = Config.empty) :
    MessageRule(config, "A function type taking or returning a function type hides control flow in an API.") {

    override fun visitFunctionType(type: KtFunctionType) {
        super.visitFunctionType(type)
        val parts = type.parameters.mapNotNull { it.typeReference } +
            listOfNotNull(type.receiverTypeReference, type.returnTypeReference)
        if (parts.none { it.unwrapFunctionType() != null }) return
        if (type.isInPublicApiDeclaration()) {
            flag(type, "Replace the callback-accepting-callback with an intention-revealing interface.")
        }
    }

    private fun PsiElement.isInPublicApiDeclaration(): Boolean {
        val owner = generateSequence(parent) { it.parent }
            .firstOrNull { it is KtDeclaration && it !is KtParameter && it !is KtParameterList }
        val isApiDeclaration = when (owner) {
            is KtNamedFunction -> !owner.isLocal
            is KtProperty -> !owner.isLocal
            is KtTypeAlias, is KtConstructor<*> -> true
            else -> false
        }
        return isApiDeclaration && generateSequence(this) { it.parent }
            .filterIsInstance<KtModifierListOwner>()
            .none { it.hasModifier(KtTokens.PRIVATE_KEYWORD) }
    }
}
