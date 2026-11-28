package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Test

class NestedFunctionTypeTest {
    private val rule = NestedFunctionType()

    @Test
    fun lint_continuationPassingParameter_reported() {
        val code = """
            suspend fun submit(raw: ByteArray, around: (suspend (suspend () -> Result) -> Result)?) {}
        """.trimIndent()
        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun lint_callbackAcceptingCallbackInPropertyTypealiasAndConstructor_reported() {
        val code = """
            val p: ((Int) -> Unit) -> Unit = {}
            typealias T = ((Int) -> Unit) -> Unit
            class A(val c: ((Int) -> Unit) -> Unit)
            fun f(): ((Int) -> Unit) -> Unit = {}
        """.trimIndent()
        assertEquals(4, rule.lint(code).size)
    }

    @Test
    fun lint_receiverIsFunctionType_reported() {
        val code = """
            fun g(x: ((Int) -> Unit).() -> Unit) {}
        """.trimIndent()
        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun lint_functionTypeReturnsFunctionType_reported() {
        val code = """
            fun f(x: () -> () -> Unit): () -> Unit = x()
        """.trimIndent()
        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun lint_plainFunctionTypes_notReported() {
        val code = """
            fun f(y: (Int) -> Unit, z: List<() -> Unit>): () -> Unit = { y(1) }
        """.trimIndent()
        assertEquals(0, rule.lint(code).size)
    }

    @Test
    fun lint_privateAndLocalDeclarations_notReported() {
        val code = """
            private fun f(x: ((Int) -> Unit) -> Unit) {}
            private val p: ((Int) -> Unit) -> Unit = {}
            class A(private val c: ((Int) -> Unit) -> Unit)
            private class B { fun f(x: ((Int) -> Unit) -> Unit) {} }
            fun g() {
                val local: ((Int) -> Unit) -> Unit = {}
                val lambda = { x: ((Int) -> Unit) -> Unit -> }
            }
        """.trimIndent()
        assertEquals(0, rule.lint(code).size)
    }
}
