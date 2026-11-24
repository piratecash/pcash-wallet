package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.api.Config
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression

class SynchronizedLock(config: Config = Config.empty) :
    MessageRule(config, "Thread monitors are unsafe across coroutine suspension points.") {

    override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
        super.visitAnnotationEntry(annotationEntry)
        if (annotationEntry.shortName?.asString() == "Synchronized") flag(annotationEntry, MESSAGE)
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (expression.calleeName() == "synchronized" && expression.lambdaArguments.isNotEmpty()) {
            flag(expression, MESSAGE)
        }
    }

    private companion object {
        const val MESSAGE = "Use Mutex, kotlin.concurrent.atomics or limitedParallelism(1) instead of synchronized."
    }
}
