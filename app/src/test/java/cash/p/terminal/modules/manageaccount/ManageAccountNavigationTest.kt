package cash.p.terminal.modules.manageaccount

import cash.p.terminal.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManageAccountNavigationTest {
    @Test
    fun accountRemoval_waitsForUnlinkDialogThenClosesManagementDestination() {
        assertFalse(shouldCloseManageAccount(true, R.id.unlinkConfirmationDialog))
        assertTrue(shouldCloseManageAccount(true, R.id.manageAccountFragment))
        assertFalse(shouldCloseManageAccount(false, R.id.manageAccountFragment))
    }
}
