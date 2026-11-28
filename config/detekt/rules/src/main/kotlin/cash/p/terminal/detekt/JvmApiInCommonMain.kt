package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.api.Config
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtImportDirective

class JvmApiInCommonMain(config: Config = Config.empty) :
    MessageRule(config, "JVM-only API blocks a future iOS target; use a multiplatform replacement.") {

    private val forbiddenImports: List<String> = valueOrDefault("forbiddenImports", DEFAULT_IMPORTS)

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)
        val path = importDirective.importPath?.pathStr ?: return
        if (forbiddenImports.any { path == it || path.startsWith("$it.") }) {
            flag(importDirective, "`$path` is JVM-only; use the multiplatform replacement.")
        }
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        val call = expression.selectorExpression as? KtCallExpression ?: return
        if (call.calleeName() == "currentTimeMillis" && expression.receiverExpression.text in SYSTEM_NAMES) {
            flag(expression, "`System.currentTimeMillis()` is JVM-only; use `kotlin.time.Clock`.")
        }
    }

    private companion object {
        val SYSTEM_NAMES = setOf("System", "java.lang.System")
        val DEFAULT_IMPORTS = listOf(
            "io.reactivex",
            "com.google.gson",
            "retrofit2",
            "okhttp3",
            "java.util.UUID",
            "java.util.concurrent",
            "java.time.Instant",
            "com.squareup.moshi",
            "kotlin.jvm.Volatile",
            "kotlinx.coroutines.runBlocking",
        )
    }
}
