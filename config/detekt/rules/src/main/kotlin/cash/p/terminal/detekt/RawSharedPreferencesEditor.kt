package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.api.Config
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassInitializer
import org.jetbrains.kotlin.psi.KtContainerNodeForControlStructureBody
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression

class RawSharedPreferencesEditor(config: Config = Config.empty) :
    MessageRule(config, "Raw SharedPreferences editor can silently drop a write.") {

    override fun visitQualifiedExpression(expression: KtQualifiedExpression) {
        super.visitQualifiedExpression(expression)
        if (!expression.isOutermostInChain()) return
        val chain = expression.flattenChain()
        val terminal = chain.last().calleeName()
        val rawEdit = chain.any { it is KtCallExpression && it.calleeName() == "edit" && it.valueArguments.isEmpty() }
        val consumedCommit = terminal == "commit" && !expression.isValueUnused()
        if (rawEdit && !consumedCommit) flag(expression, "Use the KTX `edit { }` instead of the raw editor.")
    }

    // Walks up through if/when branches to whoever receives the value; a loop body never passes it on.
    private fun KtExpression.isValueUnused(): Boolean = when (val owner = parent) {
        is KtBlockExpression -> owner.statements.last() != this || owner.isValueUnused()
        is KtFunctionLiteral -> false
        is KtDeclarationWithBody, is KtClassInitializer -> this is KtBlockExpression
        is KtContainerNodeForControlStructureBody -> when (val structure = owner.parent) {
            is KtLoopExpression -> true
            is KtIfExpression -> structure.isValueUnused()
            else -> false
        }
        is KtWhenEntry -> (owner.parent as? KtWhenExpression)?.isValueUnused() == true
        else -> false
    }
}
