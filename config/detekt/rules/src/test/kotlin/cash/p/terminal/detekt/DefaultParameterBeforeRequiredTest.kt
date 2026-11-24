package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultParameterBeforeRequiredTest {
    private val rule = DefaultParameterBeforeRequired()

    @Test
    fun lint_requiredAfterDefaultInFunction_reportedPerParameter() {
        val code = """
            fun f(a: Int = 1, b: Int, c: Int) {}
        """.trimIndent()
        assertEquals(2, rule.lint(code).size)
    }

    @Test
    fun lint_requiredAfterDefaultInConstructors_reported() {
        val code = """
            class A(val a: Int = 1, val b: Int) {
                constructor(x: String = "", y: String) : this(1, 2)
            }
        """.trimIndent()
        assertEquals(2, rule.lint(code).size)
    }

    @Test
    fun lint_defaultsLastOrVararg_notReported() {
        val code = """
            fun f(a: Int, b: Int = 1, vararg c: Int) {}
            fun g(a: Int = 1, vararg c: Int) {}
        """.trimIndent()
        assertEquals(0, rule.lint(code).size)
    }

    @Test
    fun lint_overrideFunction_notReported() {
        val code = """
            class A : B() {
                override fun f(a: Int = 1, b: Int) {}
            }
        """.trimIndent()
        assertEquals(0, rule.lint(code).size)
    }

    @Test
    fun lint_composableContentSlotLast_notReported() {
        val code = """
            @Composable
            fun A(title: String = "", content: @Composable () -> Unit) {}
            @Composable
            fun B(title: String = "", content: @Composable (() -> Unit)?) {}
        """.trimIndent()
        assertEquals(0, rule.lint(code).size)
    }

    @Test
    fun lint_composableNonSlotOrNonLastOrPlainFunction_reported() {
        val code = """
            @Composable
            fun A(title: String = "", onClick: () -> Unit) {}
            @Composable
            fun B(title: String = "", content: @Composable () -> Unit, other: Int) {}
            fun C(title: String = "", content: @Composable () -> Unit) {}
        """.trimIndent()
        assertEquals(4, rule.lint(code).size)
    }
}
