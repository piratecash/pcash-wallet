package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Test

class RawSharedPreferencesEditorTest {
    private val rule = RawSharedPreferencesEditor()

    @Test
    fun lint_editApply_reported() {
        val code = """
            fun f(prefs: Prefs) {
                prefs.edit().putString("k", "v").apply()
            }
        """.trimIndent()
        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun lint_editCommitAsStatement_reported() {
        val code = """
            fun f(prefs: Prefs) {
                prefs.edit().putBoolean("k", true).commit()
                println()
            }
        """.trimIndent()
        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun lint_editCommitDiscardedInConditionalOrLoop_reported() {
        val code = """
            fun save(prefs: Prefs, enabled: Boolean, keys: List<String>) {
                if (enabled) {
                    prefs.edit().putBoolean("enabled", true).commit()
                } else if (keys.isEmpty()) prefs.edit().clear().commit()
                when {
                    enabled -> prefs.edit().remove("a").commit()
                }
                for (key in keys) { prefs.edit().remove(key).commit() }
            }
        """.trimIndent()
        assertEquals(4, rule.lint(code).size)
    }

    @Test
    fun lint_editChainWithoutApplyOrCommit_reported() {
        val code = """
            fun save(prefs: Prefs) {
                prefs.edit().putString("key", "value")
            }
        """.trimIndent()
        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun lint_editCommitResultUsed_notReported() {
        val code = """
            fun f(prefs: Prefs): Boolean {
                val ok = prefs.edit().putBoolean("k", true).commit()
                return prefs.edit().remove("k").commit() && ok
            }
            fun g(prefs: Prefs) = prefs.edit().remove("k").commit()
            fun h(prefs: Prefs) = run {
                prefs.edit().remove("k").commit()
            }
            fun i(prefs: Prefs, key: String): Boolean =
                if (key.isNotEmpty()) {
                    prefs.edit().remove(key).commit()
                } else {
                    true
                }
        """.trimIndent()
        assertEquals(0, rule.lint(code).size)
    }

    @Test
    fun lint_ktxEdit_notReported() {
        val code = """
            fun f(prefs: Prefs) {
                prefs.edit { putString("k", "v") }
                prefs.edit(commit = true) { putString("k", "v") }
            }
        """.trimIndent()
        assertEquals(0, rule.lint(code).size)
    }
}
