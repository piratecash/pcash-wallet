package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewModelContextParameterTest {
    private val rule = ViewModelContextParameter()

    @Test
    fun lint_contextInViewModel_reported() {
        val code = """
            class AViewModel(private val context: android.content.Context, val id: Int) : ViewModel()
        """.trimIndent()
        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun lint_nullableContextInViewModelUiStateClass_reported() {
        val code = """
            class State(val context: Context?) : ViewModelUiState<Int>
        """.trimIndent()
        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun lint_contextInNonViewModel_notReported() {
        val code = """
            class Helper(val context: Context) : Runnable
        """.trimIndent()
        assertEquals(0, rule.lint(code).size)
    }

    @Test
    fun lint_viewModelWithoutContext_notReported() {
        val code = """
            class AViewModel(val repo: Repo) : ViewModel()
        """.trimIndent()
        assertEquals(0, rule.lint(code).size)
    }
}
