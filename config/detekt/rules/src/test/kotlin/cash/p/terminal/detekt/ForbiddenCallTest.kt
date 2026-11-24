package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.test.lint
import io.gitlab.arturbosch.detekt.test.TestConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ForbiddenCallTest {
    private val rule = ForbiddenCall()

    @Test
    fun lint_timberCalls_reportedOncePerCall() {
        val code = """
            fun f() {
                Timber.d("a")
                Timber.tag("t").w("b")
                Timber.e(Exception(), "c")
            }
        """.trimIndent()
        assertEquals(3, rule.lint(code).size)
    }

    @Test
    fun lint_collectAsState_reported() {
        val code = """
            @Composable
            fun F(flow: Flow<Int>) {
                val a by flow.collectAsState()
                val b = collectAsState()
            }
        """.trimIndent()
        assertEquals(2, rule.lint(code).size)
    }

    @Test
    fun lint_kermitAndLifecycleVariant_notReported() {
        val code = """
            fun f(flow: Flow<Int>) {
                logger.d { "a" }
                flow.collectAsStateWithLifecycle()
                timber.d("lowercase receiver")
            }
        """.trimIndent()
        assertEquals(0, rule.lint(code).size)
    }

    @Test
    fun lint_customConfig_overridesDefaults() {
        val custom = ForbiddenCall(TestConfig("receivers" to listOf("Log"), "functions" to listOf("legacy")))
        assertEquals(2, custom.lint("fun f() { Log.d(\"a\"); legacy() }").size)
        assertEquals(0, custom.lint("fun f() { Timber.d(\"a\") }").size)
    }
}
