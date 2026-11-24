package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtTypeReference

/** Base for rules that report one message per offending element; the rule id is the class name. */
abstract class MessageRule(config: Config, description: String) : Rule(config) {

    override val issue = Issue(javaClass.simpleName, Severity.Style, description, Debt.FIVE_MINS)

    protected fun flag(element: PsiElement, message: String) =
        report(CodeSmell(issue, Entity.from(element), message))
}

/** `a.b().c()` becomes `[a, b(), c()]`. */
internal fun KtExpression.flattenChain(): List<KtExpression> = when (this) {
    is KtQualifiedExpression -> receiverExpression.flattenChain() + listOfNotNull(selectorExpression)
    else -> listOf(this)
}

internal fun KtExpression.isOutermostInChain(): Boolean =
    (parent as? KtQualifiedExpression)?.receiverExpression != this

internal fun KtExpression.calleeName(): String? = when (this) {
    is KtCallExpression -> (calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
    is KtQualifiedExpression -> selectorExpression?.calleeName()
    else -> null
}

internal fun KtTypeReference.unwrapFunctionType(): KtFunctionType? {
    var type = typeElement
    while (type is KtNullableType) type = type.innerType
    return type as? KtFunctionType
}

/** Short name of the type: without package, type arguments and nullability. */
internal fun KtTypeReference.shortName(): String =
    text.substringBefore('<').removeSuffix("?").substringAfterLast('.').trim()

internal fun KtClassOrObject.superTypeShortNames(): List<String> =
    superTypeListEntries.mapNotNull { it.typeReference?.shortName() }

internal const val VIEW_MODEL_UI_STATE = "ViewModelUiState"
