package cash.p.terminal.modules.restoreaccount

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.modules.restoreaccount.restoreshamir.RestoreShamirPage
import cash.p.terminal.modules.restoreaccount.restoreshamir.ShamirPrefill
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OpenShamirRestoreTest {

    @Test
    fun openShamirRestore_insideRestoreFlow_finishesAtFlowPopTarget() {
        val flow = RestoreAccountPage(ManageAccountsModule.Input(OriginPage::class, popOffInclusive = true))
        val navigation = HSNavigation(NavBackStack<HSPage>(OriginPage(), flow))

        navigation.openShamirRestore(PREFILL)

        val input = assertIs<RestoreShamirPage>(navigation.backStack.last()).input
        assertEquals(RestoreShamirPage.Input(OriginPage::class, true, PREFILL), input)
    }

    @Test
    fun openShamirRestore_flowWithoutInput_returnsToRestoreAccountPage() {
        val navigation = HSNavigation(NavBackStack<HSPage>(OriginPage(), RestoreAccountPage(null)))

        navigation.openShamirRestore(PREFILL)

        val input = assertIs<RestoreShamirPage>(navigation.backStack.last()).input
        assertEquals(RestoreShamirPage.Input(RestoreAccountPage::class, false, PREFILL), input)
    }

    private class OriginPage : HSPage() {
        @Composable
        override fun GetContent(navigation: HSNavigation) = Unit
    }

    private companion object {
        val PREFILL = ShamirPrefill(listOf("academic"), "")
    }
}
