package cash.p.terminal.uicompose

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import cash.p.terminal.ui_compose.BaseComposeFragment
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BaseComposeFragmentInputTest {

    @Test
    fun getInput_currentDestinationBelongsToChild_readsOwningFragmentInput() {
        val navController = childNavController("child")
        val fragment = TestFragment().apply {
            arguments = inputArguments("parent")
        }

        assertEquals("parent", fragment.inputMarker(navController))
        verify(exactly = 0) { navController.currentBackStackEntry }
    }

    @Test
    fun getInput_fragmentInputMissing_doesNotBorrowChildInput() {
        val navController = childNavController("child")

        assertNull(TestFragment().inputMarker(navController))
        verify(exactly = 0) { navController.currentBackStackEntry }
    }

    private fun childNavController(marker: String): NavController {
        val childEntry = mockk<NavBackStackEntry> {
            every { arguments } returns inputArguments(marker)
        }
        return mockk {
            every { currentBackStackEntry } returns childEntry
        }
    }

    private fun inputArguments(marker: String): Bundle = Bundle().apply {
        putParcelable("input", Bundle().apply { putString("marker", marker) })
    }

    private class TestFragment : BaseComposeFragment() {
        @Composable
        override fun GetContent(navController: NavController) = Unit

        fun inputMarker(navController: NavController): String? =
            getInput<Bundle>()?.getString("marker")
    }
}
