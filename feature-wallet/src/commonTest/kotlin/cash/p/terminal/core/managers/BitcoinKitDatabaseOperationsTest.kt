package cash.p.terminal.core.managers

import cash.p.dogecoinkit.DogecoinKit
import io.horizontalsystems.bitcoincash.BitcoinCashKit
import io.horizontalsystems.bitcoincash.MainNetBitcoinCash
import io.horizontalsystems.bitcoincore.storage.DatabaseMigrationResult
import io.horizontalsystems.bitcoinkit.BitcoinKit
import io.horizontalsystems.cosantakit.CosantaKit
import io.horizontalsystems.dashkit.DashKit
import io.horizontalsystems.ecash.ECashKit
import io.horizontalsystems.litecoinkit.LitecoinKit
import io.horizontalsystems.piratecashkit.PirateCashKit
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class BitcoinKitDatabaseOperationsTest {
    private val operations = DefaultBitcoinKitDatabaseOperations()
    private val databaseKey = ByteArray(32) { it.toByte() }

    @Before
    fun setUp() {
        mockkObject(
            BitcoinKit.Companion,
            BitcoinCashKit.Companion,
            ECashKit.Companion,
            LitecoinKit.Companion,
            DashKit.Companion,
            DogecoinKit.Companion,
            CosantaKit.Companion,
            PirateCashKit.Companion,
        )
        coEvery { BitcoinKit.migrateDatabases(any(), any(), any(), any()) } returns MIGRATION_RESULT
        coEvery { BitcoinCashKit.migrateDatabases(any(), any(), any(), any()) } returns MIGRATION_RESULT
        coEvery { ECashKit.migrateDatabases(any(), any(), any(), any()) } returns MIGRATION_RESULT
        coEvery { LitecoinKit.migrateDatabases(any(), any(), any(), any()) } returns MIGRATION_RESULT
        coEvery { DashKit.migrateDatabases(any(), any(), any(), any()) } returns MIGRATION_RESULT
        coEvery { DogecoinKit.migrateDatabases(any(), any(), any(), any()) } returns MIGRATION_RESULT
        coEvery { CosantaKit.migrateDatabases(any(), any(), any(), any()) } returns MIGRATION_RESULT
        coEvery { PirateCashKit.migrateDatabases(any(), any(), any(), any()) } returns MIGRATION_RESULT
        every { BitcoinKit.clear(any(), any(), any()) } returns Unit
        every { BitcoinCashKit.clear(any(), any(), any()) } returns Unit
        every { ECashKit.clear(any(), any(), any()) } returns Unit
        every { LitecoinKit.clear(any(), any(), any(), any()) } returns Unit
        every { LitecoinKit.clearMweb(any(), any(), any(), any()) } returns Unit
        every { DashKit.clear(any(), any(), any()) } returns Unit
        every { DogecoinKit.clear(any(), any(), any()) } returns Unit
        every { CosantaKit.clear(any(), any(), any()) } returns Unit
        every { PirateCashKit.clear(any(), any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun migrate_account_invokesEverySupportedMainnetDatabaseGroup() = runTest {
        operations.migrate(DATA_DIR, WALLET_ID, databaseKey)

        coVerify(exactly = 1) {
            BitcoinKit.migrateDatabases(DATA_DIR, BitcoinKit.NetworkType.MainNet, WALLET_ID, matchingKey())
            BitcoinCashKit.migrateDatabases(
                DATA_DIR,
                matchingBitcoinCashNetwork(MainNetBitcoinCash.CoinType.Type0),
                WALLET_ID,
                matchingKey(),
            )
            BitcoinCashKit.migrateDatabases(
                DATA_DIR,
                matchingBitcoinCashNetwork(MainNetBitcoinCash.CoinType.Type145),
                WALLET_ID,
                matchingKey(),
            )
            ECashKit.migrateDatabases(DATA_DIR, ECashKit.NetworkType.MainNet, WALLET_ID, matchingKey())
            LitecoinKit.migrateDatabases(DATA_DIR, LitecoinKit.NetworkType.MainNet, WALLET_ID, matchingKey())
            DashKit.migrateDatabases(DATA_DIR, DashKit.NetworkType.MainNet, WALLET_ID, matchingKey())
            DogecoinKit.migrateDatabases(DATA_DIR, DogecoinKit.NetworkType.MainNet, WALLET_ID, matchingKey())
            CosantaKit.migrateDatabases(DATA_DIR, CosantaKit.NetworkType.MainNet, WALLET_ID, matchingKey())
            PirateCashKit.migrateDatabases(DATA_DIR, PirateCashKit.NetworkType.MainNet, WALLET_ID, matchingKey())
        }
    }

    @Test
    fun clear_account_invokesEverySupportedMainnetDatabaseGroup() {
        operations.clear(DATA_DIR, MWEB_DATA_DIR, WALLET_ID)

        verify(exactly = 1) {
            BitcoinKit.clear(DATA_DIR, BitcoinKit.NetworkType.MainNet, WALLET_ID)
            BitcoinCashKit.clear(
                DATA_DIR,
                matchingBitcoinCashNetwork(MainNetBitcoinCash.CoinType.Type0),
                WALLET_ID,
            )
            BitcoinCashKit.clear(
                DATA_DIR,
                matchingBitcoinCashNetwork(MainNetBitcoinCash.CoinType.Type145),
                WALLET_ID,
            )
            ECashKit.clear(DATA_DIR, ECashKit.NetworkType.MainNet, WALLET_ID)
            LitecoinKit.clear(DATA_DIR, MWEB_DATA_DIR, LitecoinKit.NetworkType.MainNet, WALLET_ID)
            DashKit.clear(DATA_DIR, DashKit.NetworkType.MainNet, WALLET_ID)
            DogecoinKit.clear(DATA_DIR, DogecoinKit.NetworkType.MainNet, WALLET_ID)
            CosantaKit.clear(DATA_DIR, CosantaKit.NetworkType.MainNet, WALLET_ID)
            PirateCashKit.clear(DATA_DIR, PirateCashKit.NetworkType.MainNet, WALLET_ID)
        }
    }

    @Test
    fun clearMweb_account_invokesOnlyLitecoinMwebClear() {
        operations.clearMweb(DATA_DIR, MWEB_DATA_DIR, WALLET_ID)

        verify(exactly = 1) {
            LitecoinKit.clearMweb(DATA_DIR, MWEB_DATA_DIR, LitecoinKit.NetworkType.MainNet, WALLET_ID)
        }
        verify(exactly = 0) {
            BitcoinKit.clear(any(), any(), any())
            BitcoinCashKit.clear(any(), any(), any())
            ECashKit.clear(any(), any(), any())
            LitecoinKit.clear(any(), any(), any(), any())
            DashKit.clear(any(), any(), any())
            DogecoinKit.clear(any(), any(), any())
            CosantaKit.clear(any(), any(), any())
            PirateCashKit.clear(any(), any(), any())
        }
    }

    private fun MockKMatcherScope.matchingKey() = match<ByteArray> {
        it.contentEquals(databaseKey)
    }

    private fun MockKMatcherScope.matchingBitcoinCashNetwork(coinType: MainNetBitcoinCash.CoinType) =
        match<BitcoinCashKit.NetworkType> {
            it is BitcoinCashKit.NetworkType.MainNet && it.coinType == coinType
        }

    private companion object {
        const val DATA_DIR = "/app/databases"
        const val MWEB_DATA_DIR = "/app/no-backup"
        const val WALLET_ID = "wallet-id"
        val MIGRATION_RESULT = DatabaseMigrationResult(0, 0)
    }
}
