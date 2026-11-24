package cash.p.terminal.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Test

class NestedViewModelScopeLaunchTest {
    private val rule = NestedViewModelScopeLaunch()

    @Test
    fun lint_launchInsideLaunch_reported() {
        val code = """
            class VM : ViewModel() {
                fun load() = viewModelScope.launch {
                    viewModelScope.launch { work() }
                }
            }
        """.trimIndent()
        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun lint_launchInsideCollectIndexed_reported() {
        val code = """
            class VM : ViewModel() {
                suspend fun observe() {
                    flow.collectIndexed { _, item ->
                        viewModelScope.launch { update(item) }
                    }
                }
            }
        """.trimIndent()
        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun lint_launchInsideDeferredCallback_notReported() {
        val code = """
            class VM : ViewModel() {
                fun bind(button: Button) = viewModelScope.launch {
                    button.setOnClickListener { viewModelScope.launch { work() } }
                }
            }
        """.trimIndent()
        assertEquals(0, rule.lint(code).size)
    }

    @Test
    fun lint_launchInsideInlineLambdasOfLaunch_reported() {
        val code = """
            class VM : ViewModel() {
                fun load() = viewModelScope.launch {
                    items.forEach { viewModelScope.launch { work(it) } }
                    runCatching { fetch() }.onSuccess { viewModelScope.launch { work(it) } }
                }
            }
        """.trimIndent()
        assertEquals(2, rule.lint(code).size)
    }

    @Test
    fun lint_launchInsideCollectDeep_reported() {
        val code = """
            class VM : ViewModel() {
                init {
                    viewModelScope.launch {
                        flow.collect { item ->
                            if (item > 0) { viewModelScope.launch(io) { work() } }
                        }
                    }
                }
            }
        """.trimIndent()
        assertEquals(1, rule.lint(code).size)
    }

    @Test
    fun lint_helperWithViewModelScopeLaunchCalledInCollect_reported() {
        val code = """
            class VM : ViewModel() {
                init {
                    viewModelScope.launch {
                        flow.collect { update(); this@VM.update() }
                    }
                }
                private fun update() {
                    viewModelScope.launch(io) { work() }
                }
            }
        """.trimIndent()
        assertEquals(2, rule.lint(code).size)
    }

    @Test
    fun lint_plainLaunchInside_notReported() {
        val code = """
            class VM : ViewModel() {
                fun load() = viewModelScope.launch {
                    launch { work() }
                }
            }
        """.trimIndent()
        assertEquals(0, rule.lint(code).size)
    }

    @Test
    fun lint_helperUsingPlainLaunchOrSuspend_notReported() {
        val code = """
            class VM : ViewModel() {
                init {
                    viewModelScope.launch {
                        flow.collect { update(); other() }
                    }
                }
                private fun update(scope: CoroutineScope) { scope.launch { work() } }
                private suspend fun other() { withContext(io) { work() } }
            }
        """.trimIndent()
        assertEquals(0, rule.lint(code).size)
    }

    @Test
    fun lint_topLevelViewModelScopeLaunchAndHelperCallOutsideCoroutine_notReported() {
        val code = """
            class VM : ViewModel() {
                fun load() { viewModelScope.launch { work() } }
                fun onClick() { update() }
                private fun update() { viewModelScope.launch { work() } }
            }
        """.trimIndent()
        assertEquals(0, rule.lint(code).size)
    }
}
