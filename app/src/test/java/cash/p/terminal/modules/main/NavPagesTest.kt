package cash.p.terminal.modules.main

import cash.p.terminal.navigation.HSPage
import io.github.classgraph.ClassGraph
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NavPagesTest {

    // An `object` page would share per-occurrence state (result key, transition, uuid) between entries.
    @Test
    fun hsPageSubclasses_scanned_noneIsKotlinObject() {
        ClassGraph()
            .enableClassInfo()
            .acceptPackages("cash.p.terminal", "io.horizontalsystems")
            .scan()
            .use { scan ->
                val pages = scan.getSubclasses(HSPage::class.java.name).filter { !it.isAbstract }
                assertTrue(pages.any { it.name == MainPage::class.java.name }, "scan found no pages")

                val objectPages = pages.filter { it.loadClass().kotlin.objectInstance != null }
                assertEquals(emptyList(), objectPages.map { it.name })
            }
    }
}
