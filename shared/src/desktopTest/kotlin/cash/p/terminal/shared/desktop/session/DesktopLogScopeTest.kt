package cash.p.terminal.shared.desktop.session

import cash.p.terminal.core.managers.UserLevelLogScope
import cash.p.terminal.shared.desktop.DesktopLogScope
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.VisibleAccounts
import cash.p.terminal.wallet.managers.UserManager
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopLogScopeTest {
    private val userManager = mockk<UserManager> { every { getUserLevel() } returns 1 }
    private val accountManager = mockk<IAccountManager> {
        every { visibleAccounts } returns VisibleAccounts(1, setOf("visible"))
    }
    private val scope = DesktopLogScope(UserLevelLogScope(userManager, accountManager))

    @Test
    fun userLevel_safeEnteredThroughTheLevelAbove_isTheSameAsAfterADirectEntry() {
        assertEquals(0, scope.userLevel)
    }

    @Test
    fun stampFor_visibleAccount_isTheSafeLevelNotTheUserLevel() {
        assertEquals(0, scope.stampFor("visible"))
    }

    @Test
    fun stampFor_accountOutsideTheCurrentLevel_isNull() {
        assertNull(scope.stampFor("hidden"))
    }
}
