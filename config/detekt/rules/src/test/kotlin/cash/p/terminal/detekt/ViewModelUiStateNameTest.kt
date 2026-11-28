package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewModelUiStateNameTest {
    private val rule = ViewModelUiStateName()

    @Test
    fun lint_uiStateWithoutViewModelSuffix_reported() {
        val code = """
            class Foo : ViewModelUiState<Int>
            class Bar : base.ViewModelUiState<Int>()
        """.trimIndent()
        assertEquals(2, rule.lint(code).size)
    }

    @Test
    fun lint_viewModelSuffixOrOtherSupertype_notReported() {
        val code = """
            class FooViewModel : ViewModelUiState<Int>
            class Baz : SomethingElse
        """.trimIndent()
        assertEquals(0, rule.lint(code).size)
    }
}
