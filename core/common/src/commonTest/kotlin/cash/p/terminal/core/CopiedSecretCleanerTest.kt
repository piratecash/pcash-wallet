package cash.p.terminal.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CopiedSecretCleanerTest {

    private class FakeClipboard(private val now: () -> Long) : CopiedSecretClipboard {
        var expiresAt: Long? = null
        var readable = true
        var readFailures = 0
        var clearFailures = 0
        var reads = 0
        var cleared = false

        fun copySecret() {
            expiresAt = now() + COPIED_SECRET_LIFETIME_MILLIS
            cleared = false
        }

        fun copySomethingElse() {
            expiresAt = null
        }

        override fun copiedSecretState(): CopiedSecretState {
            reads++
            if (readFailures > 0) {
                readFailures--
                throw IllegalStateException("clipboard busy")
            }
            if (!readable) return CopiedSecretState.Unreadable
            val deadline = expiresAt ?: return CopiedSecretState.NotOurs
            return CopiedSecretState.Pending(deadline - now())
        }

        override fun clear() {
            if (clearFailures > 0) {
                clearFailures--
                throw IllegalStateException("clipboard busy")
            }
            expiresAt = null
            cleared = true
        }
    }

    private fun TestScope.createCleaner(): Pair<FakeClipboard, CopiedSecretCleaner> {
        val clipboard = FakeClipboard { testScheduler.currentTime }
        return clipboard to CopiedSecretCleaner(clipboard, backgroundScope)
    }

    // advanceUntilIdle() ignores backgroundScope work, so time is moved explicitly.
    private fun TestScope.advanceFarAhead() {
        advanceTimeBy(10 * COPIED_SECRET_LIFETIME_MILLIS)
        runCurrent()
    }

    @Test
    fun check_pendingSecret_clearsAtDeadlineNotBefore() = runTest {
        val (clipboard, cleaner) = createCleaner()
        clipboard.copySecret()
        cleaner.check()

        advanceTimeBy(COPIED_SECRET_LIFETIME_MILLIS - 1)
        assertFalse(clipboard.cleared)

        advanceTimeBy(2)
        assertTrue(clipboard.cleared)
    }

    @Test
    fun check_clipboardReplacedBeforeDeadline_doesNotClear() = runTest {
        val (clipboard, cleaner) = createCleaner()
        clipboard.copySecret()
        cleaner.check()
        runCurrent()

        clipboard.copySomethingElse()
        advanceFarAhead()

        assertFalse(clipboard.cleared)
    }

    @Test
    fun check_unreadableAtDeadline_doesNotClearAndStops() = runTest {
        val (clipboard, cleaner) = createCleaner()
        clipboard.copySecret()
        cleaner.check()
        runCurrent()

        clipboard.readable = false
        advanceFarAhead()
        val readsWhenStopped = clipboard.reads
        advanceFarAhead()

        assertFalse(clipboard.cleared)
        assertEquals(readsWhenStopped, clipboard.reads)
    }

    @Test
    fun check_unreadableAtDeadline_nextCheckWhenReadable_clearsExpiredSecret() = runTest {
        val (clipboard, cleaner) = createCleaner()
        clipboard.copySecret()
        cleaner.check()
        runCurrent()
        clipboard.readable = false
        advanceFarAhead()

        clipboard.readable = true
        cleaner.check()
        runCurrent()

        assertTrue(clipboard.cleared)
    }

    @Test
    fun check_secondSecretCopiedWhileWaiting_clearsAtLaterDeadline() = runTest {
        val (clipboard, cleaner) = createCleaner()
        clipboard.copySecret()
        cleaner.check()
        advanceTimeBy(COPIED_SECRET_LIFETIME_MILLIS / 2)

        clipboard.copySecret()
        cleaner.check()
        advanceTimeBy(COPIED_SECRET_LIFETIME_MILLIS - 1)
        assertFalse(clipboard.cleared)

        advanceTimeBy(2)
        assertTrue(clipboard.cleared)
    }

    @Test
    fun check_readThrows_retriesUntilReadable_thenClears() = runTest {
        val (clipboard, cleaner) = createCleaner()
        clipboard.copySecret()
        clipboard.readFailures = 10
        cleaner.check()

        advanceFarAhead()

        assertTrue(clipboard.cleared)
    }

    @Test
    fun check_clearThrowsRepeatedlyBeyondFiveTimes_keepsRetryingUntilCleared() = runTest {
        val (clipboard, cleaner) = createCleaner()
        clipboard.copySecret()
        clipboard.clearFailures = 10
        cleaner.check()

        advanceFarAhead()

        assertTrue(clipboard.cleared)
        assertEquals(0, clipboard.clearFailures)
    }
}
