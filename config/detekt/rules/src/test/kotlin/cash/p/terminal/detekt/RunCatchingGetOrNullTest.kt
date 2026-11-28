package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Test

class RunCatchingGetOrNullTest {
    private val rule = RunCatchingGetOrNull()

    @Test
    fun lint_runCatchingGetOrNull_reported() {
        val code = """
            fun f() = runCatching { risky() }.getOrNull()
        """.trimIndent()
        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun lint_receiverRunCatchingGetOrNull_reported() {
        val code = """
            fun f(x: Foo) = x.runCatching { risky() }.getOrNull()
        """.trimIndent()
        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun lint_runCatchingOtherTerminal_notReported() {
        val code = """
            fun f() = runCatching { risky() }.getOrDefault(1)
            fun g(r: Result<Int>) = r.getOrNull()
        """.trimIndent()
        assertEquals(0, rule.lint(code).size)
    }
}
