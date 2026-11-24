package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.api.Config
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression

class RunCatchingGetOrNull(config: Config = Config.empty) :
    MessageRule(config, "`runCatching { }.getOrNull()` allocates a Result wrapper.") {

    override fun visitQualifiedExpression(expression: KtQualifiedExpression) {
        super.visitQualifiedExpression(expression)
        val getOrNull = expression.selectorExpression as? KtCallExpression ?: return
        if (getOrNull.calleeName() == "getOrNull" && expression.receiverExpression.calleeName() == "runCatching") {
            flag(expression, "Use `tryOrNull { }` instead of `runCatching { }.getOrNull()`.")
        }
    }
}
