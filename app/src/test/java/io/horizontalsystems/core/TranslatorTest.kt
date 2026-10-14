package io.horizontalsystems.core

import android.app.Application
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Restore_Error_MnemonicWordCount
import cash.p.terminal.resources.allStringArrayResources
import cash.p.terminal.resources.allStringResources
import cash.p.terminal.strings.helpers.AndroidLocalizedResources
import cash.p.terminal.strings.helpers.LibraryInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TranslatorTest {

    @Before
    fun setUp() {
        Robolectric.setupContentProvider(LibraryInitializer::class.java)
        startKoin {
            modules(module { singleOf(::AndroidLocalizedResources) bind LocalizedResources::class })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun getString_allDefaultWorkersBusy_returns() {
        val workers = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val resources = Res.allStringResources.values.take(workers)
        val inside = CountDownLatch(workers)
        val done = CountDownLatch(workers)
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            // Uncached loads run on Dispatchers.Default, so every worker must be blocked in Translator at once.
            resources.forEach { resource ->
                scope.launch {
                    inside.countDown()
                    inside.await(5, TimeUnit.SECONDS)
                    Translator.getString(resource)
                    done.countDown()
                }
            }
            assertTrue(
                "Translator deadlocked with all Default workers busy",
                done.await(10, TimeUnit.SECONDS)
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun allStringResources_everyKey_resolvesToAndroidResource() {
        val context = RuntimeEnvironment.getApplication()
        fun missingKeys(keys: Set<String>, type: String) =
            keys.filter { context.resources.getIdentifier(it, type, context.packageName) == 0 }
        val missing = missingKeys(Res.allStringResources.keys, "string") +
            missingKeys(Res.allStringArrayResources.keys, "array")

        assertTrue("Keys without an Android resource: $missing", missing.isEmpty())
    }

    @Test
    fun getString_formatArgs_matchesAndroidString() {
        assertEquals(
            "Wrong words count. It must be between 12 or 24 words. You entered: 13",
            Translator.getString(Res.string.Restore_Error_MnemonicWordCount, 13)
        )
    }
}
