package cash.p.terminal.core.storage

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.AccountRecord
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountsStorageTest {

    private val dao = mockk<AccountsDao>()
    private val storage = AccountsStorage(mockk<AppDatabase> { every { accountsDao() } returns dao })

    @Test
    fun allAccounts_savedThorchainAndMayachainWatchAccounts_restoresTypeAndAddress() {
        val accounts = listOf(
            watchAccount("thor", AccountType.ThorchainAddress("thor1watched")),
            watchAccount("maya", AccountType.MayachainAddress("maya1watched")),
        )
        val records = mutableListOf<AccountRecord>()
        every { dao.insert(capture(records)) } just Runs
        every { dao.getAll(0) } returns records

        accounts.forEach(storage::save)

        assertEquals(listOf("thorchain_address", "mayachain_address"), records.map { it.type })
        assertEquals(accounts.map { it.type }, storage.allAccounts(0).map { it.type })
    }

    private fun watchAccount(id: String, type: AccountType) = Account(
        id = id,
        name = id,
        type = type,
        origin = AccountOrigin.Restored,
        level = 0,
    )
}
