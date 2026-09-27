package cash.p.terminal.core.storage.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.TokenType.Eip20
import cash.p.terminal.wallet.entities.TokenType.Spl
import io.horizontalsystems.core.entities.BlockchainType

@Suppress("ClassName")
object Migration_116_117 : Migration(116, 117) {

    private class SwapSide(val table: String, val suffix: String)

    private class TokenIdentity(val coinUid: String, val blockchainType: BlockchainType, tokenType: TokenType) {
        val tokenQueryId = TokenQuery(blockchainType, tokenType).id
    }

    private val swapSides = listOf(
        SwapSide("SwapProviderTransaction", "In"),
        SwapSide("SwapProviderTransaction", "Out"),
        SwapSide("PendingMultiSwap", "In"),
        SwapSide("PendingMultiSwap", "Intermediate"),
        SwapSide("PendingMultiSwap", "Out"),
    )

    private val coinUidColumns = swapSides.map { it.table to "coinUid${it.suffix}" } + listOf(
        "PendingTransaction" to "coinUid",
        "OfflineSignedTransaction" to "coinUid",
    )

    // V1 tokens whose coin uid is gone from V2 or no longer owns them there; the token type still identifies them.
    private val staticTokenIdentities = listOf(
        TokenIdentity("apecoin-ape", BlockchainType.Ethereum, Eip20("0x4d224452801aced8b2f0aebe155379bb5d594381")),
        TokenIdentity(
            "binance-peg-ethereum",
            BlockchainType.BinanceSmartChain,
            Eip20("0x2170ed0880ac9a755fd29b2688956bd959f933f8"),
        ),
        TokenIdentity(
            "usd-coin-bridged", BlockchainType.ArbitrumOne, Eip20("0xff970a61a04b1ca14834a43f5de4533ebddb5cc8")
        ),
        TokenIdentity("usd-coin-bridged", BlockchainType.Optimism, Eip20("0x7f5c764cbc14f9669b88837ca1490cca17c31607")),
        TokenIdentity("usd-coin-bridged", BlockchainType.Polygon, Eip20("0x2791bca1f2de4661ed88a30c99a7a9449aa84174")),
        TokenIdentity(
            "usd-coin-bridged", BlockchainType.Avalanche, Eip20("0xa7d7079b0fead91f3e65f86e8915cb59c1a4c664")
        ),
        TokenIdentity("wdash", BlockchainType.BinanceSmartChain, Eip20("0xcbfb0d98151d03ef8bb71fa668f57df5e3fb4673")),
        TokenIdentity("aave-wbtc", BlockchainType.ArbitrumOne, Eip20("0x078f358208685046a11c85e8ad32895ded33a249")),
        TokenIdentity("aave-wbtc", BlockchainType.Avalanche, Eip20("0x078f358208685046a11c85e8ad32895ded33a249")),
        TokenIdentity("aave-wbtc", BlockchainType.Optimism, Eip20("0x078f358208685046a11c85e8ad32895ded33a249")),
        TokenIdentity("aave-wbtc", BlockchainType.Polygon, Eip20("0x078f358208685046a11c85e8ad32895ded33a249")),
        TokenIdentity("aave-weth", BlockchainType.ArbitrumOne, Eip20("0xe50fa9b3c56ffb159cb0fca61f5c9d750e8128c8")),
        TokenIdentity("aave-weth", BlockchainType.Avalanche, Eip20("0xe50fa9b3c56ffb159cb0fca61f5c9d750e8128c8")),
        TokenIdentity("aave-weth", BlockchainType.Optimism, Eip20("0xe50fa9b3c56ffb159cb0fca61f5c9d750e8128c8")),
        TokenIdentity("aave-weth", BlockchainType.Polygon, Eip20("0xe50fa9b3c56ffb159cb0fca61f5c9d750e8128c8")),
        TokenIdentity(
            "arbitrum-bridged-wbtc-arbitrum-one",
            BlockchainType.ArbitrumOne,
            Eip20("0x2f2a2543b76a4166549f7aab2e75bef0aefc5b0f"),
        ),
        TokenIdentity(
            "arbitrum-bridged-weth-arbitrum-one",
            BlockchainType.ArbitrumOne,
            Eip20("0x82af49447d8a07e3bd95bd0d56f35241523fbab1"),
        ),
        TokenIdentity(
            "arbitrum-bridged-wrapped-eeth",
            BlockchainType.ArbitrumOne,
            Eip20("0x35751007a407ca6feffe80b3cb397736d2cf4dbe"),
        ),
        TokenIdentity(
            "avalanche-bridged-dai-avalanche",
            BlockchainType.Avalanche,
            Eip20("0xd586e7f844cea2f87f50152665bcbc2c279d8d70"),
        ),
        TokenIdentity(
            "avalanche-bridged-weth-avalanche",
            BlockchainType.Avalanche,
            Eip20("0x49d5c2bdffac6ce2bfdb6640f4f80f226bc10bab"),
        ),
        TokenIdentity(
            "avalanche-old-bridged-wbtc-avalanche",
            BlockchainType.Avalanche,
            Eip20("0x50b7545627a5162f82a992c33b87adc75187b218"),
        ),
        TokenIdentity(
            "binance-bridged-usdt-bnb-smart-chain",
            BlockchainType.BinanceSmartChain,
            Eip20("0x55d398326f99059ff775485246999027b3197955"),
        ),
        TokenIdentity(
            "binance-peg-busd", BlockchainType.BinanceSmartChain, Eip20("0xe9e7cea3dedca5984780bafc599bd69add087d56")
        ),
        TokenIdentity(
            "binance-peg-dai", BlockchainType.BinanceSmartChain, Eip20("0x1af3f329e8be154074d8769d1ffa4ee058b1dbc3")
        ),
        TokenIdentity(
            "binance-peg-iotex", BlockchainType.BinanceSmartChain, Eip20("0x9678e42cebeb63f23197d726b29b1cb20d0064e5")
        ),
        TokenIdentity(
            "binance-peg-shib", BlockchainType.BinanceSmartChain, Eip20("0x2859e4544c4bb03966803b044a93563bd2d0dd4d")
        ),
        TokenIdentity(
            "binance-peg-zcash-token",
            BlockchainType.BinanceSmartChain,
            Eip20("0x1ba42e5193dfa8b03d15dd1b86a3113bbbef8eeb"),
        ),
        TokenIdentity("bridged-nxpc", BlockchainType.Avalanche, Eip20("0x5e0e90e268bc247cc850c789a0db0d5c7621fb59")),
        TokenIdentity(
            "bridged-usd-coin-optimism", BlockchainType.Optimism, Eip20("0x7f5c764cbc14f9669b88837ca1490cca17c31607")
        ),
        TokenIdentity("catcoin-cash", BlockchainType.Ethereum, Eip20("0x59f4f336bf3d0c49dbfba4a74ebd2a6ace40539a")),
        TokenIdentity("fantom", BlockchainType.BinanceSmartChain, Eip20("0xad29abb318791d579433d831ed122afeaf29dcfe")),
        TokenIdentity("fantom", BlockchainType.Ethereum, Eip20("0x4e15361fd6b4bb609fa63c81a2be19d873717870")),
        TokenIdentity("jpyc", BlockchainType.Avalanche, Eip20("0x431d5dff03120afa4bdf332c61a6e1766ef37bdb")),
        TokenIdentity(
            "l2-standard-bridged-weth-optimism",
            BlockchainType.Optimism,
            Eip20("0x4200000000000000000000000000000000000006"),
        ),
        TokenIdentity(
            "magic-internet-money", BlockchainType.ArbitrumOne, Eip20("0xfea7a6a0b346362bf88a9e4a88416b77a57d6c2a")
        ),
        TokenIdentity(
            "magic-internet-money-avalanche",
            BlockchainType.Avalanche,
            Eip20("0x130966628846bfd36ff31a822705796e8cb8c18d"),
        ),
        TokenIdentity(
            "makerdao-arbitrum-bridged-dai-arbitrum-one",
            BlockchainType.ArbitrumOne,
            Eip20("0xda10009cbd5d07dd0cecc66161fc93d7c9000da1"),
        ),
        TokenIdentity(
            "makerdao-optimism-bridged-dai-optimism",
            BlockchainType.Optimism,
            Eip20("0xda10009cbd5d07dd0cecc66161fc93d7c9000da1"),
        ),
        TokenIdentity(
            "polygon-bridged-wbtc-polygon-pos",
            BlockchainType.Polygon,
            Eip20("0x1bfd67037b42cf73acf2047067bd4f2c47d9bfd6"),
        ),
        TokenIdentity(
            "polygon-pos-bridged-dai-polygon-pos",
            BlockchainType.Polygon,
            Eip20("0x8f3cf7ad23cd3cadbd9735aff958023239c6a063"),
        ),
        TokenIdentity(
            "polygon-pos-bridged-weth-polygon-pos",
            BlockchainType.Polygon,
            Eip20("0x7ceb23fd6bc0add59e62ac25578270cff1b9f619"),
        ),
        TokenIdentity(
            "rainbow-bridged-near-ethereum",
            BlockchainType.Ethereum,
            Eip20("0x85f17cf997934a597031b2e18a9ab6ebd4b9f6a4"),
        ),
        TokenIdentity("wbnb", BlockchainType.Avalanche, Eip20("0x264c1383ea520f73dd837f915ef3a732e204a493")),
        TokenIdentity("weth", BlockchainType.Fantom, Eip20("0x74b23882a30290451a17c44f4f05243b6b58c76d")),
        TokenIdentity(
            "wrapped-apecoin", BlockchainType.ArbitrumOne, Eip20("0x7f9fbf9bdd3f4105c478b996b648fe6e828a1e98")
        ),
        TokenIdentity("wrapped-apecoin", BlockchainType.Ethereum, Eip20("0x4d224452801aced8b2f0aebe155379bb5d594381")),
        TokenIdentity("wrapped-apecoin", BlockchainType.Polygon, Eip20("0xb7b31a6bc18e48888545ce79e83e06003be70930")),
        TokenIdentity("wrapped-apecoin", BlockchainType.Solana, Spl("C1MHyoTJpRTeS9AQCyspNVu2EWAYCZwmJ1jNkEArFP1f")),
        TokenIdentity(
            "wrapped-near", BlockchainType.BinanceSmartChain, Eip20("0x1fa4a73a3f0133f0025378af00236f3abdee5d63")
        ),
        TokenIdentity(
            "wrapped-peaq", BlockchainType.BinanceSmartChain, Eip20("0x8b9ee39195ea99d6ddd68030f44131116bc218f6")
        ),
        TokenIdentity("wrapped-peaq", BlockchainType.Ethereum, Eip20("0x1eef208926667594e5136e89d0e9dd6907959197")),
        TokenIdentity(
            "wrapped-solana", BlockchainType.BinanceSmartChain, Eip20("0x570a5d26f7765ecb712c0924e4de545b89fd43df")
        ),
        TokenIdentity("wrapped-somi", BlockchainType.Base, Eip20("0x47636b3188774a3e7273d85a537b9ba4ee7b2535")),
        TokenIdentity(
            "wrapped-somi", BlockchainType.BinanceSmartChain, Eip20("0xa9616e5e23ec1582c2828b025becf3ef610e266f")
        ),
        TokenIdentity("wrapped-somi", BlockchainType.Ethereum, Eip20("0x1b0f6590d21dc02b92ad3a7d00f8884dc4f1aed9")),
        TokenIdentity("wrapped-zano", BlockchainType.Ethereum, Eip20("0xdb85f6685950e285b1e611037bebe5b34e2b7d78")),
    )

    // V1 coins whose uid differed from coingecko_id: V2 lists their tokens under the coingecko_id coin.
    private val renamedCoinUids = listOf(
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

    override fun migrate(db: SupportSQLiteDatabase) {
        swapSides.forEach { db.execSQL("ALTER TABLE ${it.table} ADD COLUMN tokenQueryId${it.suffix} TEXT") }
        // Must run before the renames: it matches on the old coin uid.
        swapSides.forEach { side -> staticTokenIdentities.forEach { token -> setTokenQueryId(db, side, token) } }
        coinUidColumns.forEach { (table, column) ->
            renamedCoinUids.forEach { (old, new) ->
                db.execSQL("UPDATE $table SET $column = ? WHERE $column = ?", arrayOf(new, old))
            }
        }
        renamedCoinUids.forEach { (old, new) -> renameFavorite(db, old, new) }
    }

    private fun setTokenQueryId(db: SupportSQLiteDatabase, side: SwapSide, token: TokenIdentity) {
        db.execSQL(
            "UPDATE ${side.table} SET tokenQueryId${side.suffix} = ? " +
                "WHERE coinUid${side.suffix} = ? AND blockchainType${side.suffix} = ?",
            arrayOf(token.tokenQueryId, token.coinUid, token.blockchainType.uid)
        )
    }

    private fun renameFavorite(db: SupportSQLiteDatabase, old: String, new: String) {
        db.execSQL(
            "INSERT OR IGNORE INTO FavoriteCoin(coinUid) " +
                "SELECT ? WHERE EXISTS(SELECT 1 FROM FavoriteCoin WHERE coinUid = ?)",
            arrayOf(new, old)
        )
        db.execSQL("DELETE FROM FavoriteCoin WHERE coinUid = ?", arrayOf(old))
    }
}
