package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.test.lint
import io.gitlab.arturbosch.detekt.test.TestConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class JvmApiInCommonMainTest {
    private val rule = JvmApiInCommonMain()

    @Test
    fun lint_gsonAndWildcardConcurrentImports_reported() {
        val code = """
            import com.google.gson.Gson
            import java.util.concurrent.*
            import java.util.UUID
            import kotlin.jvm.Volatile
            import io.reactivex.Observable
            import kotlinx.coroutines.runBlocking
            import java.time.Instant
            import com.squareup.moshi.Moshi
        """.trimIndent()
        assertEquals(8, rule.lint(code).size)
    }

    @Test
    fun lint_currentTimeMillis_reported() {
        val code = """
            fun a() = System.currentTimeMillis()
            fun b() = java.lang.System.currentTimeMillis()
        """.trimIndent()
        assertEquals(2, rule.lint(code).size)
    }

    @Test
    fun lint_multiplatformApis_notReported() {
        val code = """
            import kotlin.concurrent.Volatile
            import java.util.UUIDGenerator
            import kotlinx.coroutines.launch
            fun a() = System.nanoTime()
        """.trimIndent()
        assertEquals(0, rule.lint(code).size)
    }

    @Test
    fun lint_customForbiddenImports_overridesDefaults() {
        val custom = JvmApiInCommonMain(TestConfig("forbiddenImports" to listOf("foo.bar")))
        assertEquals(1, custom.lint("import foo.bar.Baz").size)
        assertEquals(0, custom.lint("import com.google.gson.Gson").size)
    }
}
