package cash.p.terminal.modules.send.bitcoin

import cash.p.terminal.core.ISendBitcoinAdapter
import cash.p.terminal.core.factories.FeeRateProviderFactory
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.amount.AmountValidator
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.entities.BlockchainType
import org.koin.core.scope.Scope

internal fun Scope.sendBitcoinViewModel(
    wallet: Wallet,
    address: Address?,
    hideAddress: Boolean,
    adapter: ISendBitcoinAdapter
): SendBitcoinViewModel {
    val adapterManager: IAdapterManager = get()
    val provider = requireNotNull(FeeRateProviderFactory.provider(wallet.token.blockchainType))
    val feeService = SendBitcoinFeeService(adapter)
    val feeRateService = SendBitcoinFeeRateService(provider)
    val amountService =
        SendBitcoinAmountService(adapter, wallet.coin.code, AmountValidator(), adapterManager, wallet)
    val addressService = SendBitcoinAddressService(adapter)
    val pluginService = SendBitcoinPluginService(wallet.token.blockchainType)
    return SendBitcoinViewModel(
        adapter = adapter,
        wallet = wallet,
        feeRateService = feeRateService,
        feeService = feeService,
        amountService = amountService,
        addressService = addressService,
        pluginService = pluginService,
        xRateService = XRateService(get(), get<CurrencyManager>().baseCurrency),
        btcBlockchainManager = get(),
        contactsRepo = get(),
        showAddressInput = !hideAddress,
        localStorage = get(),
        address = address,
        pendingRegistrar = get(),
        adapterManager = adapterManager,
        dispatcherProvider = get()
    )
}

object SendBitcoinModule {
    data class UtxoData(
        val type: UtxoType? = null,
        val value: String = "0 / 0",
    )

    enum class UtxoType {
        Auto,
        Manual
    }

    val BlockchainType.rbfSupported: Boolean
        get() = when (this) {
            BlockchainType.Bitcoin,
            BlockchainType.Litecoin -> true

            else -> false
        }
}
