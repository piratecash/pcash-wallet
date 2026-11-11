package cash.p.terminal.core.managers

import cash.p.dogecoinkit.DogecoinKit
import io.horizontalsystems.bitcoincash.BitcoinCashKit
import io.horizontalsystems.bitcoincash.MainNetBitcoinCash
import io.horizontalsystems.bitcoinkit.BitcoinKit
import io.horizontalsystems.cosantakit.CosantaKit
import io.horizontalsystems.dashkit.DashKit
import io.horizontalsystems.databasenaming.DatabaseNameResolver
import io.horizontalsystems.ecash.ECashKit
import io.horizontalsystems.litecoinkit.LitecoinKit
import io.horizontalsystems.piratecashkit.PirateCashKit

interface BitcoinKitDatabaseOperations {
    suspend fun migrate(dataDir: String, walletId: String, databaseKey: ByteArray, nameResolver: DatabaseNameResolver)
    fun clear(dataDir: String, mwebDataDir: String, walletId: String, nameResolver: DatabaseNameResolver)
    fun clearMweb(dataDir: String, mwebDataDir: String, walletId: String, nameResolver: DatabaseNameResolver)
}

class DefaultBitcoinKitDatabaseOperations : BitcoinKitDatabaseOperations {
    override suspend fun migrate(
        dataDir: String,
        walletId: String,
        databaseKey: ByteArray,
        nameResolver: DatabaseNameResolver,
    ) {
        DatabaseGroup.entries.forEach { it.migrate(dataDir, walletId, databaseKey, nameResolver) }
    }

    override fun clear(dataDir: String, mwebDataDir: String, walletId: String, nameResolver: DatabaseNameResolver) {
        DatabaseGroup.entries.forEach { it.clear(dataDir, mwebDataDir, walletId, nameResolver) }
    }

    override fun clearMweb(dataDir: String, mwebDataDir: String, walletId: String, nameResolver: DatabaseNameResolver) {
        LitecoinKit.clearMweb(dataDir, mwebDataDir, LitecoinKit.NetworkType.MainNet, walletId, nameResolver)
    }
}

private enum class DatabaseGroup {
    Bitcoin {
        override suspend fun migrate(
            dataDir: String,
            walletId: String,
            databaseKey: ByteArray,
            nameResolver: DatabaseNameResolver,
        ) {
            BitcoinKit.migrateDatabases(dataDir, BitcoinKit.NetworkType.MainNet, walletId, databaseKey, nameResolver)
        }

        override fun clear(dataDir: String, mwebDataDir: String, walletId: String, nameResolver: DatabaseNameResolver) {
            BitcoinKit.clear(dataDir, BitcoinKit.NetworkType.MainNet, walletId, nameResolver)
        }
    },
    BitcoinCashType0 {
        override suspend fun migrate(
            dataDir: String,
            walletId: String,
            databaseKey: ByteArray,
            nameResolver: DatabaseNameResolver,
        ) {
            BitcoinCashKit.migrateDatabases(
                dataDir,
                bitcoinCashNetwork(MainNetBitcoinCash.CoinType.Type0),
                walletId,
                databaseKey,
                nameResolver,
            )
        }

        override fun clear(dataDir: String, mwebDataDir: String, walletId: String, nameResolver: DatabaseNameResolver) {
            BitcoinCashKit.clear(dataDir, bitcoinCashNetwork(MainNetBitcoinCash.CoinType.Type0), walletId, nameResolver)
        }
    },
    BitcoinCashType145 {
        override suspend fun migrate(
            dataDir: String,
            walletId: String,
            databaseKey: ByteArray,
            nameResolver: DatabaseNameResolver,
        ) {
            BitcoinCashKit.migrateDatabases(
                dataDir,
                bitcoinCashNetwork(MainNetBitcoinCash.CoinType.Type145),
                walletId,
                databaseKey,
                nameResolver,
            )
        }

        override fun clear(dataDir: String, mwebDataDir: String, walletId: String, nameResolver: DatabaseNameResolver) {
            BitcoinCashKit.clear(
                dataDir,
                bitcoinCashNetwork(MainNetBitcoinCash.CoinType.Type145),
                walletId,
                nameResolver,
            )
        }
    },
    ECash {
        override suspend fun migrate(
            dataDir: String,
            walletId: String,
            databaseKey: ByteArray,
            nameResolver: DatabaseNameResolver,
        ) {
            ECashKit.migrateDatabases(dataDir, ECashKit.NetworkType.MainNet, walletId, databaseKey, nameResolver)
        }

        override fun clear(dataDir: String, mwebDataDir: String, walletId: String, nameResolver: DatabaseNameResolver) {
            ECashKit.clear(dataDir, ECashKit.NetworkType.MainNet, walletId, nameResolver)
        }
    },
    Litecoin {
        override suspend fun migrate(
            dataDir: String,
            walletId: String,
            databaseKey: ByteArray,
            nameResolver: DatabaseNameResolver,
        ) {
            LitecoinKit.migrateDatabases(dataDir, LitecoinKit.NetworkType.MainNet, walletId, databaseKey, nameResolver)
        }

        override fun clear(dataDir: String, mwebDataDir: String, walletId: String, nameResolver: DatabaseNameResolver) {
            LitecoinKit.clear(dataDir, mwebDataDir, LitecoinKit.NetworkType.MainNet, walletId, nameResolver)
        }
    },
    Dash {
        override suspend fun migrate(
            dataDir: String,
            walletId: String,
            databaseKey: ByteArray,
            nameResolver: DatabaseNameResolver,
        ) {
            DashKit.migrateDatabases(dataDir, DashKit.NetworkType.MainNet, walletId, databaseKey, nameResolver)
        }

        override fun clear(dataDir: String, mwebDataDir: String, walletId: String, nameResolver: DatabaseNameResolver) {
            DashKit.clear(dataDir, DashKit.NetworkType.MainNet, walletId, nameResolver)
        }
    },
    Dogecoin {
        override suspend fun migrate(
            dataDir: String,
            walletId: String,
            databaseKey: ByteArray,
            nameResolver: DatabaseNameResolver,
        ) {
            DogecoinKit.migrateDatabases(dataDir, DogecoinKit.NetworkType.MainNet, walletId, databaseKey, nameResolver)
        }

        override fun clear(dataDir: String, mwebDataDir: String, walletId: String, nameResolver: DatabaseNameResolver) {
            DogecoinKit.clear(dataDir, DogecoinKit.NetworkType.MainNet, walletId, nameResolver)
        }
    },
    Cosanta {
        override suspend fun migrate(
            dataDir: String,
            walletId: String,
            databaseKey: ByteArray,
            nameResolver: DatabaseNameResolver,
        ) {
            CosantaKit.migrateDatabases(dataDir, CosantaKit.NetworkType.MainNet, walletId, databaseKey, nameResolver)
        }

        override fun clear(dataDir: String, mwebDataDir: String, walletId: String, nameResolver: DatabaseNameResolver) {
            CosantaKit.clear(dataDir, CosantaKit.NetworkType.MainNet, walletId, nameResolver)
        }
    },
    PirateCash {
        override suspend fun migrate(
            dataDir: String,
            walletId: String,
            databaseKey: ByteArray,
            nameResolver: DatabaseNameResolver,
        ) {
            PirateCashKit.migrateDatabases(
                dataDir,
                PirateCashKit.NetworkType.MainNet,
                walletId,
                databaseKey,
                nameResolver,
            )
        }

        override fun clear(dataDir: String, mwebDataDir: String, walletId: String, nameResolver: DatabaseNameResolver) {
            PirateCashKit.clear(dataDir, PirateCashKit.NetworkType.MainNet, walletId, nameResolver)
        }
    };

    abstract suspend fun migrate(
        dataDir: String,
        walletId: String,
        databaseKey: ByteArray,
        nameResolver: DatabaseNameResolver,
    )
    abstract fun clear(dataDir: String, mwebDataDir: String, walletId: String, nameResolver: DatabaseNameResolver)
}

private fun bitcoinCashNetwork(coinType: MainNetBitcoinCash.CoinType) =
    BitcoinCashKit.NetworkType.MainNet(coinType)
