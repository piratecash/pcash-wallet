package cash.p.terminal.wallet.storage

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import cash.p.terminal.wallet.models.CoinHistoricalPrice
import cash.p.terminal.wallet.models.CoinPrice
import cash.p.terminal.wallet.models.GlobalMarketInfo
import cash.p.terminal.wallet.models.SyncerState
import co.touchlab.kermit.Logger
import java.io.InputStream
import java.io.InputStreamReader

const val MARKET_DATABASE_NAME = "marketKitDatabase"

private val logger = Logger.withTag("MarketDatabase")

@Database(
    entities = [
        CoinRecord::class,
        BlockchainRecord::class,
        TokenRecord::class,
        CoinPrice::class,
        CoinHistoricalPrice::class,
        GlobalMarketInfo::class,
        SyncerState::class,
    ],
    version = 17,
    exportSchema = false
)
@TypeConverters(DatabaseTypeConverters::class)
@ConstructedBy(MarketDatabaseConstructor::class)
abstract class MarketDatabase : RoomDatabase() {
    internal abstract fun coinDao(): CoinDao
    abstract fun coinPriceDao(): CoinPriceDao
    abstract fun coinHistoricalPriceDao(): CoinHistoricalPriceDao
    abstract fun globalMarketInfoDao(): GlobalMarketInfoDao
    abstract fun syncerStateDao(): SyncerStateDao

    companion object {

        internal fun loadInitialCoins(
            connection: SQLiteConnection,
            source: InitialCoinsSource
        ): Int {
            var insertCount = 0

            try {
                // Using INSERT OR REPLACE to handle conflicts automatically
                InputStreamReader(source.open()).forEachLine { statement ->
                    connection.execSQL(statement)
                    insertCount++
                }
                logger.i { "Initial coins loaded: $insertCount statements executed" }
            } catch (error: Exception) {
                logger.w { "Error in loadInitialCoins(): ${error.message ?: error::class.simpleName}" }
            }

            return insertCount
        }
    }
}

@Suppress("KotlinNoActualForExpect")
expect object MarketDatabaseConstructor : RoomDatabaseConstructor<MarketDatabase> {
    override fun initialize(): MarketDatabase
}

/** The checked-in coins dump: an Android asset, a classpath resource on desktop. */
interface InitialCoinsSource {
    fun open(): InputStream
}

/** Seeds a freshly created (or destructively recreated) database from the checked-in dump. */
internal class InitialCoinsCallback(
    private val source: InitialCoinsSource
) : RoomDatabase.Callback() {

    override fun onCreate(connection: SQLiteConnection) {
        val loadedCount = MarketDatabase.loadInitialCoins(connection, source)
        logger.i { "onCreate Loaded coins count: $loadedCount" }
    }

    override fun onDestructiveMigration(connection: SQLiteConnection) {
        val loadedCount = MarketDatabase.loadInitialCoins(connection, source)
        logger.i { "onDestructiveMigration Loaded coins count: $loadedCount" }
    }
}
