package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

class AppSingletonAccessRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "AppSingletonAccess",
        severity = Severity.Style,
        description = "Dependencies must be injected via constructor/Koin, not read from App/CoreApp.",
        debt = Debt.TWENTY_MINS
    )

    private val singletons = listOf(
        Singleton("App", "cash.p.terminal.core"),
        Singleton("CoreApp", "io.horizontalsystems.core"),
    )
    private var receivers: Set<String> = emptySet()
    private var companionImportPrefixes: List<String> = emptyList()

    override fun visitKtFile(file: KtFile) {
        val usable = singletons.filter { it.isUsableIn(file) }
        receivers = usable.flatMap { listOf(it.name, "${it.name}.Companion") }.toSet()
        companionImportPrefixes = singletons.map { "${it.fqName}.Companion." }
        super.visitKtFile(file)
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        val selector = (expression.selectorExpression as? KtNameReferenceExpression)?.getReferencedName()
        if (selector == null || selector == "instance" || selector == "Companion") return
        if (expression.receiverExpression.text !in receivers) return
        reportAccess(expression, expression.text)
    }

    override fun visitImportDirective(importDirective: KtImportDirective) {
        super.visitImportDirective(importDirective)
        val path = importDirective.importPath?.pathStr ?: return
        val aliasesSingleton = importDirective.aliasName != null && singletons.any { it.fqName == path }
        val importsCompanionMember = companionImportPrefixes.any { path.startsWith(it) } &&
            !path.endsWith(".instance")
        if (aliasesSingleton || importsCompanionMember) reportAccess(importDirective, "import $path")
    }

    private fun reportAccess(element: PsiElement, text: String) = report(
        CodeSmell(
            issue = issue,
            entity = Entity.from(element),
            message = "Inject the dependency via constructor/Koin instead of `$text`. " +
                "Only `App.instance` is allowed, for an unavoidable Context."
        )
    )

    private class Singleton(val name: String, val packageName: String) {
        val fqName = "$packageName.$name"

        fun isUsableIn(file: KtFile): Boolean {
            val declaresIt = file.declarations.any { it is KtClass && it.name == name }
            if (declaresIt) return false
            return file.packageFqName.asString() == packageName ||
                file.importDirectives.any { it.importPath?.pathStr == fqName && it.aliasName == null }
        }
    }
}
