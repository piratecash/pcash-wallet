package cash.p.terminal.core.storage

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import cash.p.terminal.core.storage.migrations.Migration_116_117
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("ClassName")
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration_116_117Test {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context())
            .name(null) // in-memory database
            .callback(object : SupportSQLiteOpenHelper.Callback(START_VERSION) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        db = helper.writableDatabase
        createVersion116Tables()
    }

    @After
    fun tearDown() {
        helper.close()
    }

    @Test
    fun migrate116To117_addsTokenQueryIdColumnsMatchingRoomSchema() {
        Migration_116_117.migrate(db)

        val room = Room.inMemoryDatabaseBuilder(context(), AppDatabase::class.java).build()
        try {
            val roomDb = room.openHelper.readableDatabase
            NEW_COLUMNS.forEach { (table, columns) ->
                assertEquals(table, columnInfo(roomDb, table, columns), columnInfo(db, table, columns))
            }
        } finally {
            room.close()
        }
    }

    @Test
    fun migrate116To117_everyStaticToken_getsExactTokenQueryIdOnEverySide() {
        STATIC_TOKENS.forEachIndexed { index, token ->
            insertSwapProviderTransaction(token.coinUid, token.chain, token.coinUid, token.chain)
            insertPendingMultiSwap(
                id = "swap-%02d".format(index),
                intermediate = token.coinUid to token.chain,
                uid = token.coinUid,
                chain = token.chain,
            )
        }

        Migration_116_117.migrate(db)

        val expected = STATIC_TOKENS.map { it.expectedTokenQueryId }
        listOf("tokenQueryIdIn", "tokenQueryIdOut").forEach { column ->
            assertEquals(column, expected, strings("SELECT $column FROM SwapProviderTransaction ORDER BY date"))
        }
        listOf("tokenQueryIdIn", "tokenQueryIdIntermediate", "tokenQueryIdOut").forEach { column ->
            assertEquals(column, expected, strings("SELECT $column FROM PendingMultiSwap ORDER BY id"))
        }
    }

    @Test
    fun tokenQueryId_staticTokenLiterals_matchTokenQueryFormat() {
        STATIC_TOKENS.forEach { token ->
            assertEquals(
                token.expectedTokenQueryId,
                TokenQuery(BlockchainType.fromUid(token.chain), token.tokenType).id
            )
        }
    }

    @Test
    fun migrate116To117_retiredUidOnOtherChain_keepsTokenQueryIdNull() {
        insertSwapProviderTransaction("usd-coin-bridged", "ethereum", "litecoin", "litecoin")
        insertPendingMultiSwap(intermediate = "wdash" to "ethereum")

        Migration_116_117.migrate(db)

        assertNull(single("SELECT tokenQueryIdIn FROM SwapProviderTransaction"))
        assertNull(single("SELECT tokenQueryIdOut FROM SwapProviderTransaction"))
        assertNull(single("SELECT tokenQueryIdIntermediate FROM PendingMultiSwap"))
        assertNull(single("SELECT tokenQueryIdIn FROM PendingMultiSwap"))
    }

    @Test
    fun migrate116To117_ownerSwitchedUidOnOtherChain_keepsTokenQueryIdNull() {
        insertSwapProviderTransaction("binance-bridged-usdt-bnb-smart-chain", "ethereum", "wrapped-apecoin", "base")
        insertPendingMultiSwap(intermediate = "aave-weth" to "ethereum")

        Migration_116_117.migrate(db)

        assertNull(single("SELECT tokenQueryIdIn FROM SwapProviderTransaction"))
        assertNull(single("SELECT tokenQueryIdOut FROM SwapProviderTransaction"))
        assertNull(single("SELECT tokenQueryIdIntermediate FROM PendingMultiSwap"))
    }

    @Test
    fun migrate116To117_everyRenamedUid_relabeledInEveryCoinUidColumn() {
        RENAMED_UIDS.keys.forEachIndexed { index, oldUid ->
            insertSwapProviderTransaction(oldUid, "ethereum", oldUid, "ethereum")
            insertPendingMultiSwap(id = "swap-$index", intermediate = oldUid to "ethereum", uid = oldUid)
            db.execSQL("INSERT INTO PendingTransaction(id, coinUid) VALUES (?, ?)", arrayOf("pending-$index", oldUid))
            db.execSQL(
                "INSERT INTO OfflineSignedTransaction(id, coinUid) VALUES (?, ?)",
                arrayOf("offline-$index", oldUid)
            )
        }

        Migration_116_117.migrate(db)

        val expected = RENAMED_UIDS.values.sorted()
        COIN_UID_COLUMNS.forEach { (table, column) ->
            assertEquals("$table.$column", expected, sortedStrings("SELECT $column FROM $table"))
        }
    }

    @Test
    fun migrate116To117_unrelatedAndNullLabels_untouched() {
        insertSwapProviderTransaction("litecoin", "litecoin", "bitcoin", "bitcoin")
        db.execSQL("INSERT INTO OfflineSignedTransaction(id, coinUid) VALUES ('offline', NULL)")

        Migration_116_117.migrate(db)

        assertEquals("litecoin", single("SELECT coinUidIn FROM SwapProviderTransaction"))
        assertEquals("bitcoin", single("SELECT coinUidOut FROM SwapProviderTransaction"))
        assertNull(single("SELECT coinUid FROM OfflineSignedTransaction"))
    }

    @Test
    fun migrate116To117_favorites_collapseOntoNewUids() {
        listOf("thorchain-secured-bitcoin", "zano-bridged-wrapped-bitcoin", "bitcoin", "wdash", "litecoin")
            .forEach { db.execSQL("INSERT INTO FavoriteCoin(coinUid) VALUES (?)", arrayOf(it)) }

        Migration_116_117.migrate(db)

        assertEquals(listOf("bitcoin", "dash", "litecoin"), sortedStrings("SELECT coinUid FROM FavoriteCoin"))
    }

    private fun createVersion116Tables() {
        db.execSQL(
            "CREATE TABLE SwapProviderTransaction (date INTEGER PRIMARY KEY NOT NULL, " +
                "coinUidIn TEXT NOT NULL, blockchainTypeIn TEXT NOT NULL, " +
                "coinUidOut TEXT NOT NULL, blockchainTypeOut TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE PendingMultiSwap (id TEXT PRIMARY KEY NOT NULL, " +
                "coinUidIn TEXT NOT NULL, blockchainTypeIn TEXT NOT NULL, " +
                "coinUidIntermediate TEXT NOT NULL, blockchainTypeIntermediate TEXT NOT NULL, " +
                "coinUidOut TEXT NOT NULL, blockchainTypeOut TEXT NOT NULL)"
        )
        db.execSQL("CREATE TABLE PendingTransaction (id TEXT PRIMARY KEY NOT NULL, coinUid TEXT NOT NULL)")
        db.execSQL("CREATE TABLE OfflineSignedTransaction (id TEXT PRIMARY KEY NOT NULL, coinUid TEXT)")
        db.execSQL("CREATE TABLE FavoriteCoin (coinUid TEXT PRIMARY KEY NOT NULL)")
    }

    private var nextDate = 1L

    private fun insertSwapProviderTransaction(
        coinUidIn: String,
        chainIn: String,
        coinUidOut: String,
        chainOut: String,
    ) {
        db.execSQL(
            "INSERT INTO SwapProviderTransaction VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any>(nextDate++, coinUidIn, chainIn, coinUidOut, chainOut)
        )
    }

    private fun insertPendingMultiSwap(
        intermediate: Pair<String, String>,
        id: String = "swap",
        uid: String = "binancecoin",
        chain: String = "ethereum",
    ) {
        db.execSQL(
            "INSERT INTO PendingMultiSwap VALUES (?, ?, ?, ?, ?, ?, ?)",
            arrayOf(id, uid, chain, intermediate.first, intermediate.second, uid, chain)
        )
    }

    private fun strings(sql: String): List<String?> = db.query(sql).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
    }

    private fun single(sql: String): String? = strings(sql).single()

    private fun sortedStrings(sql: String): List<String?> = strings(sql).sortedWith(nullsFirst())

    private fun columnInfo(database: SupportSQLiteDatabase, table: String, columns: List<String>) =
        database.query("PRAGMA table_info($table)").use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    if (name !in columns) continue
                    put(
                        name,
                        listOf(
                            cursor.getString(cursor.getColumnIndexOrThrow("type")),
                            cursor.getString(cursor.getColumnIndexOrThrow("notnull")),
                            cursor.getString(cursor.getColumnIndexOrThrow("dflt_value")),
                        )
                    )
                }
            }
        }

    private fun context() = ApplicationProvider.getApplicationContext<Context>()

    private class StaticToken(
        val coinUid: String,
        val chain: String,
        val tokenType: TokenType,
        val expectedTokenQueryId: String,
    )

    private companion object {
        const val START_VERSION = 116

        // From the V1 snapshot.
        val RETIRED_TOKENS = listOf(
            StaticToken(
                "apecoin-ape", "ethereum",
                TokenType.Eip20("0x4d224452801aced8b2f0aebe155379bb5d594381"),
                "ethereum|eip20:0x4d224452801aced8b2f0aebe155379bb5d594381",
            ),
            StaticToken(
                "binance-peg-ethereum", "binance-smart-chain",
                TokenType.Eip20("0x2170ed0880ac9a755fd29b2688956bd959f933f8"),
                "binance-smart-chain|eip20:0x2170ed0880ac9a755fd29b2688956bd959f933f8",
            ),
            StaticToken(
                "usd-coin-bridged", "arbitrum-one",
                TokenType.Eip20("0xff970a61a04b1ca14834a43f5de4533ebddb5cc8"),
                "arbitrum-one|eip20:0xff970a61a04b1ca14834a43f5de4533ebddb5cc8",
            ),
            StaticToken(
                "usd-coin-bridged", "optimistic-ethereum",
                TokenType.Eip20("0x7f5c764cbc14f9669b88837ca1490cca17c31607"),
                "optimistic-ethereum|eip20:0x7f5c764cbc14f9669b88837ca1490cca17c31607",
            ),
            StaticToken(
                "usd-coin-bridged", "polygon-pos",
                TokenType.Eip20("0x2791bca1f2de4661ed88a30c99a7a9449aa84174"),
                "polygon-pos|eip20:0x2791bca1f2de4661ed88a30c99a7a9449aa84174",
            ),
            StaticToken(
                "usd-coin-bridged", "avalanche",
                TokenType.Eip20("0xa7d7079b0fead91f3e65f86e8915cb59c1a4c664"),
                "avalanche|eip20:0xa7d7079b0fead91f3e65f86e8915cb59c1a4c664",
            ),
            StaticToken(
                "wdash", "binance-smart-chain",
                TokenType.Eip20("0xcbfb0d98151d03ef8bb71fa668f57df5e3fb4673"),
                "binance-smart-chain|eip20:0xcbfb0d98151d03ef8bb71fa668f57df5e3fb4673",
            ),
        )

        // V1 tokens whose coin stopped being the V2 owner of the contract; a sample of the migration's list.
        val OWNER_SWITCHED_TOKENS = listOf(
            StaticToken(
                "binance-bridged-usdt-bnb-smart-chain", "binance-smart-chain",
                TokenType.Eip20("0x55d398326f99059ff775485246999027b3197955"),
                "binance-smart-chain|eip20:0x55d398326f99059ff775485246999027b3197955",
            ),
            StaticToken(
                "arbitrum-bridged-weth-arbitrum-one", "arbitrum-one",
                TokenType.Eip20("0x82af49447d8a07e3bd95bd0d56f35241523fbab1"),
                "arbitrum-one|eip20:0x82af49447d8a07e3bd95bd0d56f35241523fbab1",
            ),
            StaticToken(
                "wrapped-apecoin", "solana",
                TokenType.Spl("C1MHyoTJpRTeS9AQCyspNVu2EWAYCZwmJ1jNkEArFP1f"),
                "solana|spl:C1MHyoTJpRTeS9AQCyspNVu2EWAYCZwmJ1jNkEArFP1f",
            ),
            StaticToken(
                "aave-weth", "arbitrum-one",
                TokenType.Eip20("0xe50fa9b3c56ffb159cb0fca61f5c9d750e8128c8"),
                "arbitrum-one|eip20:0xe50fa9b3c56ffb159cb0fca61f5c9d750e8128c8",
            ),
            StaticToken(
                "aave-weth", "polygon-pos",
                TokenType.Eip20("0xe50fa9b3c56ffb159cb0fca61f5c9d750e8128c8"),
                "polygon-pos|eip20:0xe50fa9b3c56ffb159cb0fca61f5c9d750e8128c8",
            ),
        )

        val STATIC_TOKENS = RETIRED_TOKENS + OWNER_SWITCHED_TOKENS

        val NEW_COLUMNS = mapOf(
            "SwapProviderTransaction" to listOf("tokenQueryIdIn", "tokenQueryIdOut"),
            "PendingMultiSwap" to listOf("tokenQueryIdIn", "tokenQueryIdIntermediate", "tokenQueryIdOut"),
        )

        val COIN_UID_COLUMNS = listOf(
            "SwapProviderTransaction" to "coinUidIn",
            "SwapProviderTransaction" to "coinUidOut",
            "PendingMultiSwap" to "coinUidIn",
            "PendingMultiSwap" to "coinUidIntermediate",
            "PendingMultiSwap" to "coinUidOut",
            "PendingTransaction" to "coinUid",
            "OfflineSignedTransaction" to "coinUid",
        )

        // V1 uid -> coingecko_id, from the V1 snapshot.
        val RENAMED_UIDS = mapOf(
            "apecoin-ape" to "apecoin",
            "binance-peg-ethereum" to "weth",
            "polygon-bridged-usdt-polygon" to "tether",
            "thorchain-secured-aave" to "aave",
            "thorchain-secured-avalanche-2" to "avalanche-2",
            "thorchain-secured-binance-usd" to "binance-usd",
            "thorchain-secured-binancecoin" to "binancecoin",
            "thorchain-secured-bitcoin" to "bitcoin",
            "thorchain-secured-bitcoin-cash" to "bitcoin-cash",
            "thorchain-secured-chainlink" to "chainlink",
            "thorchain-secured-cosmos" to "cosmos",
            "thorchain-secured-dai" to "dai",
            "thorchain-secured-dogecoin" to "dogecoin",
            "thorchain-secured-ethereum" to "ethereum",
            "thorchain-secured-gemini-dollar" to "gemini-dollar",
            "thorchain-secured-liquity-usd" to "liquity-usd",
            "thorchain-secured-litecoin" to "litecoin",
            "thorchain-secured-paxos-standard" to "paxos-standard",
            "thorchain-secured-ripple" to "ripple",
            "thorchain-secured-shapeshift-fox-token" to "shapeshift-fox-token",
            "thorchain-secured-solana" to "solana",
            "thorchain-secured-tether" to "tether",
            "thorchain-secured-thorstarter" to "thorstarter",
            "thorchain-secured-thorswap" to "thorswap",
            "thorchain-secured-thorwallet" to "thorwallet",
            "thorchain-secured-tron" to "tron",
            "thorchain-secured-trust-wallet-token" to "trust-wallet-token",
            "thorchain-secured-usd-coin" to "usd-coin",
            "thorchain-secured-wrapped-bitcoin" to "wrapped-bitcoin",
            "thorchain-secured-yearn-finance" to "yearn-finance",
            "usd-coin-bridged" to "usd-coin-avalanche-bridged-usdc-e",
            "wdash" to "dash",
            "zano-bridged-bnb" to "binancecoin",
            "zano-bridged-dai" to "dai",
            "zano-bridged-solana" to "solana",
            "zano-bridged-wrapped-bitcoin" to "bitcoin",
            "zano-bridged-wrapped-bitcoin-cash" to "bitcoin-cash",
            "zano-bridged-wrapped-ethereum" to "ethereum",
            "zano-bridged-wrapped-ton" to "the-open-network",
        )
    }
}
