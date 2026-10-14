package cash.p.terminal.navigation

import androidx.navigation3.runtime.NavBackStack
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Owns every change of [backStack]: result delivery, result registration cleanup and the root
 * guard live here, so the back stack must not be mutated around it.
 */
class HSNavigation(val backStack: NavBackStack<HSPage>) {

    @PublishedApi
    internal val results = ResultRegistry(backStack)

    private var resumedPage: HSPage? = null

    fun slideFromRight(page: HSPage) = mutate { add(page, NavigationType.SlideFromRight) }

    fun slideFromBottom(page: HSPage) = mutate { add(page, NavigationType.SlideFromBottom) }

    inline fun <reified T : Any> slideFromRightForResult(page: HSPage, crossinline onResult: (T) -> Unit) {
        results.register(page) { onResult(it as T) }
        slideFromRight(page)
    }

    inline fun <reified T : Any> slideFromBottomForResult(page: HSPage, crossinline onResult: (T) -> Unit) {
        results.register(page) { onResult(it as T) }
        slideFromBottom(page)
    }

    fun <T : Any> setResult(page: HSPage, result: T) = results.setResult(page, result)

    fun navigateUp(): Boolean {
        if (backStack.size <= 1) return false
        mutate { backStack.removeAt(backStack.lastIndex) }
        return true
    }

    /** Removes the entries above the last [klass] entry, and that entry too if [inclusive]. */
    fun removeLastUntil(klass: KClass<out HSPage>, inclusive: Boolean): Boolean {
        val index = backStack.indexOfLast { it::class == klass }
        if (index == -1) return false
        mutate { removeFrom(if (inclusive) index else index + 1) }
        return true
    }

    fun slideFromRightClearingBackStack(page: HSPage, popUpTo: KClass<out HSPage>) = mutate {
        val index = backStack.indexOfLast { it::class == popUpTo }
        if (index != -1) removeFrom(index + 1)
        add(page, NavigationType.SlideFromRight)
    }

    fun removeTrailingBottomSheets() = mutate { removeTrailingBottomSheetsInPlace() }

    fun lastOrNull(): HSPage? = backStack.lastOrNull()

    fun lastNonSheetOrNull(): HSPage? = backStack.lastOrNull { !it.bottomSheet }

    internal fun onPageResumed(page: HSPage) {
        resumedPage = page
    }

    internal fun onPagePaused(page: HSPage) {
        if (resumedPage === page) resumedPage = null
    }

    // Settled: no transition in progress and the host is resumed, as Nav2's RESUMED entry check.
    internal fun isTopSettled(): Boolean = resumedPage != null && lastOrNull() === resumedPage

    private fun add(page: HSPage, navType: NavigationType) {
        page.navType = navType
        if (!page.bottomSheet) removeTrailingBottomSheetsInPlace()
        backStack.add(page)
    }

    private fun removeTrailingBottomSheetsInPlace() {
        removeFrom(backStack.indexOfLast { !it.bottomSheet } + 1)
    }

    // Never removes the root entry: NavDisplay requires a non-empty back stack.
    private fun removeFrom(index: Int) {
        val from = index.coerceAtLeast(1)
        if (from < backStack.size) backStack.subList(from, backStack.size).clear()
    }

    private inline fun mutate(block: () -> Unit) {
        block()
        results.onBackStackChanged()
    }
}

/**
 * Results of pages opened for result, bound to the caller page. A result is kept (latest wins) and
 * handed over once, as soon as the caller is visible: on the stack with only sheets above it.
 */
@PublishedApi
internal class ResultRegistry(private val backStack: List<HSPage>) {

    private val registrations = mutableMapOf<String, Registration>()

    // A non-sheet page removes the sheets on top when pushed, so its caller is the page beneath them.
    fun register(page: HSPage, onResult: (Any) -> Unit) {
        val caller = checkNotNull(
            if (page.bottomSheet) backStack.lastOrNull() else backStack.lastOrNull { !it.bottomSheet }
        )
        val resultKey = randomId()
        page.resultKey = resultKey
        registrations[resultKey] = Registration(caller, onResult)
    }

    fun setResult(page: HSPage, result: Any) {
        val registration = page.resultKey?.let(registrations::get) ?: return
        registration.result = result
        deliver()
    }

    fun onBackStackChanged() {
        registrations.values.removeAll { registration -> backStack.none { it === registration.caller } }
        deliver()
    }

    // A callback may navigate and re-enter here, so pick one registration at a time.
    private fun deliver() {
        while (true) {
            val (key, registration) = registrations.entries
                .firstOrNull { (_, registration) -> registration.result != null && isVisible(registration.caller) }
                ?: return
            registrations.remove(key)
            registration.result?.let(registration.onResult)
        }
    }

    private fun isVisible(page: HSPage): Boolean {
        val index = backStack.indexOfLast { it === page }
        return index != -1 && backStack.subList(index + 1, backStack.size).all { it.bottomSheet }
    }

    private class Registration(val caller: HSPage, val onResult: (Any) -> Unit) {
        var result: Any? = null
    }
}

// A page still animating out must not pop the page beneath it, so only a settled top may go back.
fun HSNavigation.navigateUpSafely(): Boolean = isTopSettled() && BackNavigationGate.runIfAllowed(::navigateUp)

fun HSNavigation.removeLastUntilSafely(klass: KClass<out HSPage>, inclusive: Boolean): Boolean =
    isTopSettled() && BackNavigationGate.runIfAllowed { removeLastUntil(klass, inclusive) }

// Not gated on a settled top: result callbacks call it while the popped page is still resumed.
fun HSNavigation.slideFromRightSafely(page: HSPage): Boolean = ForwardNavigationGate.runIfAllowed {
    slideFromRight(page)
    true
}

// A page still animating out after its pop must not close the page beneath it.
fun HSNavigation.navigateUpFrom(page: HSPage): Boolean = lastOrNull() === page && navigateUp()

/** Process-global cooldown shared by every navigation of one direction, against double taps. */
internal class NavigationGate(
    private val cooldown: Duration,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private var lockedUntil: TimeMark? = null

    fun acquire(): Boolean {
        if (lockedUntil?.hasNotPassedNow() == true) return false
        lockedUntil = timeSource.markNow() + cooldown
        return true
    }

    fun release() {
        lockedUntil = null
    }

    inline fun runIfAllowed(navigate: () -> Boolean): Boolean {
        if (!acquire()) return false
        return navigate().also { navigated ->
            if (!navigated) release()
        }
    }
}

internal val BackNavigationGate = NavigationGate(650.milliseconds)
internal val ForwardNavigationGate = NavigationGate(300.milliseconds)
