package cash.p.terminal.core.storage

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.AccountRecord
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountsStorageTest {

    private val dao = mockk<AccountsDao>(relaxUnitFun = true)
    private val storage = AccountsStorage(StaticAppDatabaseProvider(databaseWith(dao)))

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

    @Test
    fun save_ledgerDevice_storesDeviceIdModelAndWalletPublicKey() {
        val record = savedRecord(AccountType.LedgerDevice("ledger-id", "nanoSP", "wallet-key"))

        assertEquals("ledger_device", record.type)
        assertEquals("ledger-id@nanoSP", record.key?.value)
        assertEquals("wallet-key", record.passphrase?.value)
    }

    @Test
    fun loadAccount_ledgerDevice_restoresAllFields() {
        val restored = roundTrip(AccountType.LedgerDevice("ledger-id", "nanoSP", "wallet-key"))

        assertEquals(
            listOf("ledger-id", "nanoSP", "wallet-key"),
            (restored as AccountType.LedgerDevice).let { listOf(it.deviceId, it.model, it.walletPublicKey) }
        )
    }

    @Test
    fun save_trezorDevice_keepsExistingRecordFormat() {
        val record = savedRecord(AccountType.TrezorDevice("trezor-id", "T3T1", "2.8.1", "wallet-key"))

        assertEquals("trezor_device", record.type)
        assertEquals("trezor-id@T3T1@2.8.1", record.key?.value)
        assertEquals("wallet-key", record.passphrase?.value)
    }

    @Test
    fun loadAccount_trezorDevice_restoresAllFields() {
        val restored = roundTrip(AccountType.TrezorDevice("trezor-id", "T3T1", "2.8.1", "wallet-key"))

        assertEquals(
            listOf("trezor-id", "T3T1", "2.8.1", "wallet-key"),
            (restored as AccountType.TrezorDevice)
                .let { listOf(it.deviceId, it.model, it.firmwareVersion, it.walletPublicKey) }
        )
    }

    @Test
    fun delete_databaseReplacedAfterFirstUse_writesToTheNewDatabase() {
        val newDao = mockk<AccountsDao>(relaxUnitFun = true)
        val provider = ReplaceableDatabaseProvider(databaseWith(dao))
        val storage = AccountsStorage(provider)
        storage.delete(ACCOUNT_ID)

        provider.database = databaseWith(newDao)
        storage.delete(ACCOUNT_ID)

        verify(exactly = 1) { dao.delete(ACCOUNT_ID) }
        verify(exactly = 1) { newDao.delete(ACCOUNT_ID) }
    }

    private fun databaseWith(dao: AccountsDao): AppDatabase = mockk { every { accountsDao() } returns dao }

    private class ReplaceableDatabaseProvider(override var database: AppDatabase) : AppDatabaseProvider

    private fun watchAccount(id: String, type: AccountType) = Account(
        id = id,
        name = id,
        type = type,
        origin = AccountOrigin.Restored,
        level = 0,
    )

    private fun assertRoundTrip(accountType: AccountType) {
        assertEquals(accountType, roundTrip(accountType))
    }

    private fun roundTrip(accountType: AccountType): AccountType? {
        val record = savedRecord(accountType)
        every { dao.loadAccount(ACCOUNT_ID) } returns record
        return storage.loadAccount(ACCOUNT_ID)?.type
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
