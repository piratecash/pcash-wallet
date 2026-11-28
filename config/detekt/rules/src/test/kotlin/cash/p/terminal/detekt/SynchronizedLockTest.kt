package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Test

class SynchronizedLockTest {
    private val rule = SynchronizedLock()

    @Test
    fun lint_synchronizedAnnotationAndBlock_reported() {
        val code = """
            class A {
                @Synchronized
                fun f() {}
                fun g() = synchronized(this) { 1 }
            }
        """.trimIndent()
        assertEquals(2, rule.lint(code).size)
    }

    @Test
    fun lint_mutex_notReported() {
        val code = """
            class A {
                private val mutex = Mutex()
                suspend fun f() = mutex.withLock { 1 }
            }
        """.trimIndent()
        assertEquals(0, rule.lint(code).size)
    }
}
