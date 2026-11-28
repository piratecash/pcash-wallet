package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.api.Config
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class ForbiddenCall(config: Config = Config.empty) :
    MessageRule(config, "Calls that have a project-mandated replacement.") {

    private val receivers: List<String> = valueOrDefault("receivers", listOf("Timber"))
    private val functions: List<String> = valueOrDefault("functions", listOf("collectAsState"))

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        if (!expression.isOutermostInChain()) return
        val root = (expression.flattenChain().first() as? KtNameReferenceExpression)?.getReferencedName()
        if (root in receivers) flag(expression, hint(root.orEmpty()))
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val name = expression.calleeName()
        if (name in functions) flag(expression, hint(name.orEmpty()))
    }

    private fun hint(name: String) = when (name) {
        "Timber" -> "Log with Kermit (`co.touchlab.kermit.Logger`) instead of Timber."
        "collectAsState" -> "Use `collectAsStateWithLifecycle` instead of `collectAsState`."
        else -> "`$name` is forbidden."
    }
}
