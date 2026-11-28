package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Test

class NotNullAssertionTest {
    private val rule = NotNullAssertion()

    @Test
    fun lint_bangBang_reported() {
        val code = """
            fun f(s: String?) = s!!.length + s!!.length
        """.trimIndent()
        assertEquals(2, rule.lint(code).size)
    }

    @Test
    fun lint_safeAlternatives_notReported() {
        val code = """
            fun f(s: String?, b: Boolean) = s?.length ?: if (!b) 1 else s.orEmpty().length
        """.trimIndent()
        assertEquals(0, rule.lint(code).size)
    }
}
