package cash.p.terminal.modules.watchaddress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.modules.address.AddressHandlerFactory
import io.horizontalsystems.core.entities.BlockchainType
import org.koin.java.KoinJavaComponent.inject

object WatchAddressModule {

    val supportedBlockchainTypes = buildList {
        add(BlockchainType.Ethereum)
        add(BlockchainType.Tron)
        add(BlockchainType.Ton)
        add(BlockchainType.Bitcoin)
        add(BlockchainType.BitcoinCash)
        add(BlockchainType.Litecoin)
        add(BlockchainType.Dash)
        add(BlockchainType.ECash)
        add(BlockchainType.Stellar)
    }

    class Factory : ViewModelProvider.Factory {
        private val restoreSettingsManager: RestoreSettingsManager by inject(RestoreSettingsManager::class.java)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val service = WatchAddressService(
                App.accountManager, App.walletActivator, App.accountFactory, App.marketKit,
                App.evmBlockchainManager, restoreSettingsManager
            )
            val addressHandlerFactory = AddressHandlerFactory(AppConfigProvider.udnApiKey)
            val addressParserChain = addressHandlerFactory.parserChain(
                blockchainTypes = supportedBlockchainTypes,
                blockchainTypesWithEns = listOf(BlockchainType.Ethereum)
            )
            return WatchAddressViewModel(service, addressParserChain) as T
        }
    }
}
