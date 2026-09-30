package cash.p.terminal.core.managers

import cash.p.beam.BeamLogLevel
import cash.p.beam.BeamSdkConfig
import cash.p.beam.BeamWalletFactory
import cash.p.beam.BeamWalletSession
import cash.p.beam.RestoreSource
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext
import cash.p.beam.BeamNetwork as SdkBeamNetwork

class BeamSessionFactory internal constructor(
    private val keyProvider: BeamDatabaseKeyProvider,
    private val storageLocator: BeamStorageLocator,
    private val dispatcherProvider: DispatcherProvider,
    private val restoreSettingsManager: RestoreSettingsManager,
    private val factory: Factory,
) {
    constructor(
        keyProvider: BeamDatabaseKeyProvider,
        storageLocator: BeamStorageLocator,
        dispatcherProvider: DispatcherProvider,
        restoreSettingsManager: RestoreSettingsManager,
        factory: BeamWalletFactory = BeamWalletFactory(),
    ) : this(keyProvider, storageLocator, dispatcherProvider, restoreSettingsManager, SdkFactory(factory))

    internal suspend fun open(account: Account, network: BeamNetwork): BeamWalletSession =
        withContext(dispatcherProvider.io) {
            keyProvider.ensureAvailable(account.id)
            val mnemonic = requireNotNull(account.type as? AccountType.Mnemonic) {
                "BEAM requires a mnemonic account"
            }
            val config = config(account.id, network)
            val key = keyProvider.keyForInitialization(account.id, network)
            try {
                if (storageLocator.databaseFile(account.id, network).exists()) {
                    factory.openExisting(config, key.bytes)
                } else {
                    create(account, mnemonic, config, key)
                }
            } finally {
                key.bytes.fill(0)
            }
        }

    internal suspend fun ensureAvailable(accountId: String) = withContext(dispatcherProvider.io) {
        keyProvider.ensureAvailable(accountId)
    }

    private fun config(accountId: String, network: BeamNetwork) = BeamSdkConfig(
        network = when (network) {
            BeamNetwork.Mainnet -> SdkBeamNetwork.Mainnet
        },
        storagePath = storageLocator.storagePath(accountId, network).absolutePath,
        logLevel = BeamLogLevel.None,
        // Stated rather than inherited. The SDK defaults this to false to match the official Beam
        // wallet, which accepts single-node recovery; arming it makes the wallet refuse to finish a
        // body scan whenever fewer than two nodes are simultaneously live, secure, at the tip and
        // node-flagged, which froze recovery silently before SDK v0.1.5. Spelling the choice out
        // here keeps a future change of the SDK default from silently changing this wallet's
        // recovery trust model; BeamSessionFactoryTest pins it.
        requireRecoveryQuorum = false,
    )

    private suspend fun create(
        account: Account,
        mnemonic: AccountType.Mnemonic,
        config: BeamSdkConfig,
        key: BeamDatabaseKeyProvider.Key,
    ): BeamWalletSession {
        val restoreRequired = restoreSettingsManager.hasBeamRestoreIntent(account)
        val seed = mnemonic.seed.copyOf()
        return try {
            // A persisted wrapper can outlive wallet.db; a new birthday could skip existing funds.
            if (!restoreRequired && account.origin == AccountOrigin.Created && key.isNew) {
                factory.createNew(config, seed, key.bytes)
            } else {
                factory.restore(config, seed, key.bytes, RestoreSource.SnapshotThenScan())
            }
        } finally {
            seed.fill(0)
        }
    }

    internal interface Factory {
        suspend fun openExisting(config: BeamSdkConfig, databaseKey: ByteArray): BeamWalletSession
        suspend fun createNew(config: BeamSdkConfig, seed: ByteArray, databaseKey: ByteArray): BeamWalletSession
        suspend fun restore(
            config: BeamSdkConfig,
            seed: ByteArray,
            databaseKey: ByteArray,
            source: RestoreSource,
        ): BeamWalletSession
    }

    private class SdkFactory(private val factory: BeamWalletFactory) : Factory {
        override suspend fun openExisting(config: BeamSdkConfig, databaseKey: ByteArray) =
            factory.openExisting(config, databaseKey)

        override suspend fun createNew(config: BeamSdkConfig, seed: ByteArray, databaseKey: ByteArray) =
            factory.createNew(config, seed, databaseKey)

        override suspend fun restore(
            config: BeamSdkConfig,
            seed: ByteArray,
            databaseKey: ByteArray,
            source: RestoreSource,
        ) = factory.restore(config, seed, databaseKey, source)
    }
}
