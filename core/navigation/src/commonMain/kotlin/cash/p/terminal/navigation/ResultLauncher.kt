package cash.p.terminal.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.result.ResultEffect

internal class Launch(val callee: HSPage, val arg: Any?)

internal class Answer(val callee: HSPage, val result: Any)

/**
 * Opens a page whose result is delivered to the composition that remembered this launcher.
 * [caller] is null for launchers outside any page; those are never forgotten.
 */
class ResultLauncher<A> internal constructor(
    private val navigation: HSNavigation,
    private val key: String,
    private val caller: HSPage?,
) {
    fun slideFromRight(page: HSPage, arg: A) {
        record(page, arg)
        navigation.slideFromRight(page)
    }

    fun slideFromBottom(page: HSPage, arg: A) {
        record(page, arg)
        navigation.slideFromBottom(page)
    }

    private fun record(page: HSPage, arg: A) {
        page.resultKey = key
        caller?.let { navigation.launcherOwners[key] = it }
        navigation.launches[key] = Launch(page, arg)
    }
}

fun ResultLauncher<Unit>.slideFromRight(page: HSPage) = slideFromRight(page, Unit)

fun ResultLauncher<Unit>.slideFromBottom(page: HSPage) = slideFromBottom(page, Unit)

/**
 * Remember unconditionally at the root of the page/screen composable, never inside a branch, lazy item,
 * sheet content or slot that can leave composition: there the result is lost or delivered late.
 */
@Composable
inline fun <reified R : Any> HSNavigation.rememberResultLauncher(
    noinline onResult: (R) -> Unit,
): ResultLauncher<Unit> = rememberResultLauncher<R, Unit> { result, _ -> onResult(result) }

/** Like the single-argument variant; [A] is the argument given at launch, handed back with the result. */
@Suppress("UNCHECKED_CAST")
@Composable
inline fun <reified R : Any, A> HSNavigation.rememberResultLauncher(
    noinline onResult: (R, A) -> Unit,
): ResultLauncher<A> = rememberAnyResultLauncher { result, arg -> onResult(result as R, arg as A) }

@PublishedApi
@Composable
internal fun <A> HSNavigation.rememberAnyResultLauncher(onResult: (Any, Any?) -> Unit): ResultLauncher<A> {
    val key = rememberSaveable { randomId() }
    val owner = LocalHSPage.current
    ResultEffect<Answer>(key, resultBus) { answer ->
        // Ignored: a launch dropped with its caller, or one a later launch of this launcher replaced.
        val launch = launches[key]?.takeIf { it.callee === answer.callee } ?: return@ResultEffect
        launches.remove(key)
        onResult(answer.result, launch.arg)
    }
    DisposableEffect(key, owner) {
        if (owner != null) launcherOwners[key] = owner
        onDispose {
            if (owner != null && !isOnBackStack(owner)) forgetLauncher(key)
        }
    }
    return remember(this, key, owner) { ResultLauncher(this, key, owner) }
}
