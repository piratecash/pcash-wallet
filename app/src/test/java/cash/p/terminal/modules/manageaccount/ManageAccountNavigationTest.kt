package cash.p.terminal.modules.manageaccount

import cash.p.terminal.modules.unlinkaccount.UnlinkAccountSheet
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageAccountNavigationTest {
    @Test
    fun accountRemoval_waitsForUnlinkSheetThenClosesManagementPage() {
        val manageAccountPage = ManageAccountPage(ManageAccountPage.Input("account-id"))

        assertFalse(shouldCloseManageAccount(true, UnlinkAccountSheet(mockk())))
        assertTrue(shouldCloseManageAccount(true, manageAccountPage))
        assertFalse(shouldCloseManageAccount(false, manageAccountPage))
    }
}
