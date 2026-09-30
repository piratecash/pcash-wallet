package cash.p.terminal.core.storage

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.AccountRecord
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountsStorageTest {

    private val dao = mockk<AccountsDao>(relaxUnitFun = true)
    private val storage = AccountsStorage(mockk { every { accountsDao() } returns dao })

    @Test
    fun save_zcashSaplingSpendingKey_roundTripsBackToTheSameType() {
        assertRoundTrip(AccountType.ZCashSaplingKey(SPENDING_KEY))
    }

    @Test
    fun save_zcashSaplingViewingKey_roundTripsBackToTheSameType() {
        assertRoundTrip(AccountType.ZCashSaplingKey(VIEWING_KEY))
    }

    @Test
    fun save_zcashSaplingKey_usesItsOwnTypeCode() {
        val record = savedRecord(AccountType.ZCashSaplingKey(SPENDING_KEY))

        assertEquals("zcash_sapling_key", record.type)
        assertEquals(SPENDING_KEY, record.key?.value)
    }

    private fun assertRoundTrip(accountType: AccountType) {
        val record = savedRecord(accountType)
        every { dao.loadAccount(ACCOUNT_ID) } returns record

        assertEquals(accountType, storage.loadAccount(ACCOUNT_ID)?.type)
    }

    private fun savedRecord(accountType: AccountType): AccountRecord {
        val record = slot<AccountRecord>()
        every { dao.insert(capture(record)) } returns Unit
        storage.save(
            Account(
                id = ACCOUNT_ID,
                name = "Sapling",
                type = accountType,
                origin = AccountOrigin.Restored,
                level = 0
            )
        )
        return record.captured
    }

    private companion object {
        const val ACCOUNT_ID = "account-id"
        const val SPENDING_KEY = "secret-extended-key-main1qsaplingspendingkey"
        const val VIEWING_KEY = "zxviews1qsaplingviewingkey"
    }
}
