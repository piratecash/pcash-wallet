package cash.p.terminal.core.managers

import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.logger.LogScope

class UserLevelLogScope(
    private val userManager: UserManager,
    private val accountManager: IAccountManager,
) : LogScope {
    /** Before the first unlock nobody is in a level yet: such rows stay with the main one. */
    override val userLevel: Int
        get() = userManager.getUserLevel().takeUnless { it == UserManager.DEFAULT_USER_LEVEL } ?: MAIN_LEVEL

    override fun stampFor(accountId: String?): Int? {
        val visible = accountManager.visibleAccounts ?: return MAIN_LEVEL.takeIf { accountId == null }
        return visible.level.takeIf { accountId == null || accountId in visible.accountIds }
    }

    private companion object {
        const val MAIN_LEVEL = 0
    }
}
