package cash.p.terminal.core.managers

import cash.p.dogecoinkit.DogecoinKit
import io.horizontalsystems.bitcoincash.BitcoinCashKit
import io.horizontalsystems.bitcoincash.MainNetBitcoinCash
import io.horizontalsystems.bitcoinkit.BitcoinKit
import io.horizontalsystems.cosantakit.CosantaKit
import io.horizontalsystems.dashkit.DashKit
import io.horizontalsystems.ecash.ECashKit
import io.horizontalsystems.litecoinkit.LitecoinKit
import io.horizontalsystems.piratecashkit.PirateCashKit

interface BitcoinKitDatabaseOperations {
    suspend fun migrate(dataDir: String, walletId: String, databaseKey: ByteArray)
    fun clear(dataDir: String, mwebDataDir: String, walletId: String)
    fun clearMweb(dataDir: String, mwebDataDir: String, walletId: String)
}

class DefaultBitcoinKitDatabaseOperations : BitcoinKitDatabaseOperations {
    override suspend fun migrate(dataDir: String, walletId: String, databaseKey: ByteArray) {
        DatabaseGroup.entries.forEach { it.migrate(dataDir, walletId, databaseKey) }
    }

    override fun clear(dataDir: String, mwebDataDir: String, walletId: String) {
        DatabaseGroup.entries.forEach { it.clear(dataDir, mwebDataDir, walletId) }
    }

    override fun clearMweb(dataDir: String, mwebDataDir: String, walletId: String) {
        LitecoinKit.clearMweb(dataDir, mwebDataDir, LitecoinKit.NetworkType.MainNet, walletId)
    }
}

private enum class DatabaseGroup {
    Bitcoin {
        override suspend fun migrate(dataDir: String, walletId: String, databaseKey: ByteArray) {
            BitcoinKit.migrateDatabases(dataDir, BitcoinKit.NetworkType.MainNet, walletId, databaseKey)
        }

        override fun clear(dataDir: String, mwebDataDir: String, walletId: String) {
            BitcoinKit.clear(dataDir, BitcoinKit.NetworkType.MainNet, walletId)
        }
    },
    BitcoinCashType0 {
        override suspend fun migrate(dataDir: String, walletId: String, databaseKey: ByteArray) {
            BitcoinCashKit.migrateDatabases(
                dataDir,
                bitcoinCashNetwork(MainNetBitcoinCash.CoinType.Type0),
                walletId,
                databaseKey,
            )
        }

        override fun clear(dataDir: String, mwebDataDir: String, walletId: String) {
            BitcoinCashKit.clear(dataDir, bitcoinCashNetwork(MainNetBitcoinCash.CoinType.Type0), walletId)
        }
    },
    BitcoinCashType145 {
        override suspend fun migrate(dataDir: String, walletId: String, databaseKey: ByteArray) {
            BitcoinCashKit.migrateDatabases(
                dataDir,
                bitcoinCashNetwork(MainNetBitcoinCash.CoinType.Type145),
                walletId,
                databaseKey,
            )
        }

        override fun clear(dataDir: String, mwebDataDir: String, walletId: String) {
            BitcoinCashKit.clear(dataDir, bitcoinCashNetwork(MainNetBitcoinCash.CoinType.Type145), walletId)
        }
    },
    ECash {
        override suspend fun migrate(dataDir: String, walletId: String, databaseKey: ByteArray) {
            ECashKit.migrateDatabases(dataDir, ECashKit.NetworkType.MainNet, walletId, databaseKey)
        }

        override fun clear(dataDir: String, mwebDataDir: String, walletId: String) {
            ECashKit.clear(dataDir, ECashKit.NetworkType.MainNet, walletId)
        }
    },
    Litecoin {
        override suspend fun migrate(dataDir: String, walletId: String, databaseKey: ByteArray) {
            LitecoinKit.migrateDatabases(dataDir, LitecoinKit.NetworkType.MainNet, walletId, databaseKey)
        }

        override fun clear(dataDir: String, mwebDataDir: String, walletId: String) {
            LitecoinKit.clear(dataDir, mwebDataDir, LitecoinKit.NetworkType.MainNet, walletId)
        }
    },
    Dash {
        override suspend fun migrate(dataDir: String, walletId: String, databaseKey: ByteArray) {
            DashKit.migrateDatabases(dataDir, DashKit.NetworkType.MainNet, walletId, databaseKey)
        }

        override fun clear(dataDir: String, mwebDataDir: String, walletId: String) {
            DashKit.clear(dataDir, DashKit.NetworkType.MainNet, walletId)
        }
    },
    Dogecoin {
        override suspend fun migrate(dataDir: String, walletId: String, databaseKey: ByteArray) {
            DogecoinKit.migrateDatabases(dataDir, DogecoinKit.NetworkType.MainNet, walletId, databaseKey)
        }

        override fun clear(dataDir: String, mwebDataDir: String, walletId: String) {
            DogecoinKit.clear(dataDir, DogecoinKit.NetworkType.MainNet, walletId)
        }
    },
    Cosanta {
        override suspend fun migrate(dataDir: String, walletId: String, databaseKey: ByteArray) {
            CosantaKit.migrateDatabases(dataDir, CosantaKit.NetworkType.MainNet, walletId, databaseKey)
        }

        override fun clear(dataDir: String, mwebDataDir: String, walletId: String) {
            CosantaKit.clear(dataDir, CosantaKit.NetworkType.MainNet, walletId)
        }
    },
    PirateCash {
        override suspend fun migrate(dataDir: String, walletId: String, databaseKey: ByteArray) {
            PirateCashKit.migrateDatabases(dataDir, PirateCashKit.NetworkType.MainNet, walletId, databaseKey)
        }

        override fun clear(dataDir: String, mwebDataDir: String, walletId: String) {
            PirateCashKit.clear(dataDir, PirateCashKit.NetworkType.MainNet, walletId)
        }
    };

    abstract suspend fun migrate(dataDir: String, walletId: String, databaseKey: ByteArray)
    abstract fun clear(dataDir: String, mwebDataDir: String, walletId: String)
}

private fun bitcoinCashNetwork(coinType: MainNetBitcoinCash.CoinType) =
    BitcoinCashKit.NetworkType.MainNet(coinType)
