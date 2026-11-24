package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.api.Config
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class NestedViewModelScopeLaunch(config: Config = Config.empty) :
    MessageRule(config, "viewModelScope.launch inside a coroutine breaks structured concurrency.") {

    private val launchingHelpers = mutableMapOf<KtClassOrObject, Set<String>>()

    override fun visitKtFile(file: KtFile) {
        launchingHelpers.clear()
        super.visitKtFile(file)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!expression.isInsideCoroutineLambda()) return
        if (expression.isViewModelScopeLaunch() || expression.callsLaunchingHelper()) {
            flag(expression, "Use plain `launch` (child of the current coroutine) or make the helper `suspend`.")
        }
    }

    private fun KtCallExpression.isViewModelScopeLaunch(): Boolean {
        val qualified = parent as? KtDotQualifiedExpression ?: return false
        return qualified.selectorExpression == this && calleeName() == "launch" &&
            qualified.receiverExpression.text.substringAfterLast('.') == "viewModelScope"
    }

    private fun KtCallExpression.callsLaunchingHelper(): Boolean {
        val name = (calleeExpression as? KtNameReferenceExpression)?.getReferencedName() ?: return false
        val qualifier = parent as? KtQualifiedExpression
        val isOwnCall = qualifier == null || qualifier.selectorExpression != this ||
            qualifier.receiverExpression is KtThisExpression
        val owner = getStrictParentOfType<KtClassOrObject>() ?: return false
        return isOwnCall && name in launchingHelpers.getOrPut(owner) { owner.launchingFunctionNames() }
    }

    private fun KtClassOrObject.launchingFunctionNames(): Set<String> =
        declarations.filterIsInstance<KtNamedFunction>()
            .filter { function ->
                function.bodyExpression?.anyDescendantOfType<KtCallExpression> { it.isViewModelScopeLaunch() } == true
            }
            .mapNotNull { it.name }
            .toSet()

    // A listener or callback lambda runs later, outside the coroutine that registered it.
    private fun KtCallExpression.isInsideCoroutineLambda() =
        generateSequence(parent) { it.parent }
            .filterIsInstance<KtLambdaExpression>()
            .map { it.argumentOwner()?.calleeName().orEmpty() }
            .takeWhile { !it.endsWith("Listener") && !it.endsWith("Callback") }
            .any { it.isCoroutineCall() }

    private fun KtLambdaExpression.argumentOwner(): KtCallExpression? {
        val argument = parent as? KtValueArgument ?: return null
        return argument.parent as? KtCallExpression ?: argument.parent?.parent as? KtCallExpression
    }

    private fun String.isCoroutineCall() = this in COROUTINE_CALLS || startsWith("collect")

    private companion object {
        val COROUTINE_CALLS = setOf(
            "launch", "async", "withContext", "coroutineScope", "supervisorScope"
        )
    }
}
