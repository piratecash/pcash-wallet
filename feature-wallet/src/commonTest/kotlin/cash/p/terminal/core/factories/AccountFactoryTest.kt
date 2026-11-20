package cash.p.terminal.core.factories

import cash.p.terminal.core.managers.DefaultUserManager
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountFactoryTest {

    private val accountManager = mockk<IAccountManager> { every { accounts } returns emptyList() }
    private val userManager = mockk<DefaultUserManager> { every { getUserLevel() } returns 0 }
    private val factory = AccountFactory(accountManager, userManager)

    @Test
    fun account_nameWithBlankLines_storesSingleLineName() {
        val account = factory.account(
            name = "Wallet 11\n\n\n",
            type = AccountType.EvmAddress("0x"),
            origin = AccountOrigin.Created,
            backedUp = false,
            fileBackedUp = false,
        )

        assertEquals("Wallet 11", account.name)
    }

    @Test
    fun watchAccount_nameWithLineBreak_storesSingleLineName() {
        val account = factory.watchAccount("Watch\nWallet\n", AccountType.EvmAddress("0x"))

        assertEquals("Watch Wallet", account.name)
    }

    @Test
    fun getUniqueName_nameDiffersFromExistingOnlyByLineBreaks_addsSuffix() {
        every { accountManager.accounts } returns listOf(existingAccount("Wallet 1"))

        assertEquals("Wallet 1 (1)", factory.getUniqueName("Wallet 1\n", emptySet()))
    }

    private fun existingAccount(name: String) = Account(
        id = name,
        name = name,
        type = AccountType.EvmAddress("0x"),
        origin = AccountOrigin.Created,
        level = 0,
    )
}
