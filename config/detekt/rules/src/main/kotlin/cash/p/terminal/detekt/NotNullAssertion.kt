package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.api.Config
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtPostfixExpression

class NotNullAssertion(config: Config = Config.empty) :
    MessageRule(config, "The `!!` operator crashes on null.") {

    override fun visitPostfixExpression(expression: KtPostfixExpression) {
        super.visitPostfixExpression(expression)
        if (expression.operationToken == KtTokens.EXCLEXCL) {
            flag(expression, "Avoid `!!`; use `?:`, `requireNotNull` or `checkNotNull`.")
        }
    }
}
