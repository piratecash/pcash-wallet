package cash.p.terminal.core.managers

import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.VisibleAccounts
import cash.p.terminal.wallet.managers.UserManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserLevelLogScopeTest {

    private val userManager = mockk<UserManager> { every { getUserLevel() } returns LEVEL }
    private val accountManager = mockk<IAccountManager> {
        every { visibleAccounts } returns VisibleAccounts(LEVEL, setOf(VISIBLE_ID))
    }
    private val scope = UserLevelLogScope(userManager, accountManager)

    @Test
    fun userLevel_always_isTheCurrentUserLevel() {
        assertEquals(LEVEL, scope.userLevel)
    }

    @Test
    fun userLevel_beforeTheFirstUnlock_isTheMainLevel() {
        every { userManager.getUserLevel() } returns UserManager.DEFAULT_USER_LEVEL

        assertEquals(0, scope.userLevel)
    }

    @Test
    fun stampFor_accountInTheSnapshot_isTheSnapshotLevel() {
        assertEquals(LEVEL, scope.stampFor(VISIBLE_ID))
    }

    @Test
    fun stampFor_accountOutsideTheSnapshot_isNull() {
        assertNull(scope.stampFor(OTHER_ID))
    }

    @Test
    fun stampFor_userLevelAlreadySwitched_isStillTheSnapshotLevel() {
        every { userManager.getUserLevel() } returns LEVEL - 1

        assertEquals(LEVEL, scope.stampFor(VISIBLE_ID))
        assertEquals(LEVEL, scope.stampFor(null))
    }

    @Test
    fun stampFor_hiddenLevel_admitsOnlyItsOwnAccounts() {
        every { accountManager.visibleAccounts } returns VisibleAccounts(HIDDEN_LEVEL, setOf(HIDDEN_ID))

        assertEquals(HIDDEN_LEVEL, scope.stampFor(HIDDEN_ID))
        assertNull(scope.stampFor(VISIBLE_ID))
    }

    @Test
    fun stampFor_noSnapshot_stampsLinesWithoutAccountWithTheMainLevelAndDropsTheRest() {
        every { accountManager.visibleAccounts } returns null

        assertEquals(0, scope.stampFor(null))
        assertNull(scope.stampFor(VISIBLE_ID))
    }

    private companion object {
        const val LEVEL = 2
        const val HIDDEN_LEVEL = -1
        const val VISIBLE_ID = "visible"
        const val OTHER_ID = "other"
        const val HIDDEN_ID = "hidden"
    }
}
