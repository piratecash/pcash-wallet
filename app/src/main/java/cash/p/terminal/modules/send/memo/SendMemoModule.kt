package cash.p.terminal.modules.send.memo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.core.ISendMemoAdapter
import cash.p.terminal.core.isNative
import cash.p.terminal.core.managers.OfflineSignedTransactionRepository
import cash.p.terminal.core.managers.OfflineTransactionPayloadEncoder
import cash.p.terminal.core.managers.PendingTransactionRegistrar
import cash.p.terminal.core.managers.RecentAddressManager
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.amount.AmountValidator
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.getMaxSendableBalance
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.DispatcherProvider
import org.koin.java.KoinJavaComponent.inject

object SendMemoModule {
    class Factory(
        private val wallet: Wallet,
        private val address: Address?,
        private val hideAddress: Boolean,
        private val adapter: ISendMemoAdapter
    ) : ViewModelProvider.Factory {
        private val adapterManager: IAdapterManager by inject(IAdapterManager::class.java)
        private val dispatcherProvider: DispatcherProvider by inject(DispatcherProvider::class.java)
        private val recentAddressManager: RecentAddressManager by inject(RecentAddressManager::class.java)
        private val offlineTransactionPayloadEncoder: OfflineTransactionPayloadEncoder by inject(
            OfflineTransactionPayloadEncoder::class.java
        )
        private val offlineSignedTransactionRepository: OfflineSignedTransactionRepository by inject(
            OfflineSignedTransactionRepository::class.java
        )
        private val pendingRegistrar: PendingTransactionRegistrar by inject(PendingTransactionRegistrar::class.java)

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val amountValidator = AmountValidator()
            val coinMaxAllowedDecimals = wallet.token.decimals

            val availableBalance = adapterManager.getMaxSendableBalance(wallet, adapter.maxSpendableBalance)
            val amountService = SendAmountService(
                amountValidator = amountValidator,
                coinCode = wallet.coin.code,
                availableBalance = availableBalance,
                leaveSomeBalanceForFee = wallet.token.type.isNative
            )
            val chain = SendMemoChain.of(wallet.token.blockchainType)
            val addressService = SendMemoAddressService(adapter, chain)
            val xRateService = XRateService(App.marketKit, App.currencyManager.baseCurrency)
            val feeToken = App.coinManager.getToken(
                TokenQuery(
                    wallet.token.blockchainType,
                    TokenType.Native
                )
            ) ?: throw IllegalArgumentException()

            return SendMemoViewModel(
                wallet,
                wallet.token,
                feeToken,
                adapter,
                coinMaxAllowedDecimals,
                xRateService,
                address,
                !hideAddress,
                amountService,
                addressService,
                App.contactsRepository,
                SendMemoMinimumAmountService(adapter),
                adapterManager,
                dispatcherProvider,
                recentAddressManager,
                offlineTransactionPayloadEncoder,
                offlineSignedTransactionRepository,
                chain,
                pendingRegistrar,
            ) as T
        }
    }
}
