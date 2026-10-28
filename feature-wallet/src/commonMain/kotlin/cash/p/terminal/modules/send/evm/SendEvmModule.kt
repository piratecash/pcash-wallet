package cash.p.terminal.modules.send.evm

import cash.p.terminal.core.ISendEthereumAdapter
import cash.p.terminal.core.isNative
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.amount.AmountValidator
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.multiswap.sendtransaction.services.SendTransactionServiceEvm
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.getMaxSendableBalance
import io.horizontalsystems.ethereumkit.models.TransactionData
import java.math.BigInteger
import java.math.RoundingMode
import io.horizontalsystems.core.CurrencyManager
import org.koin.core.scope.Scope


data class SendEvmData(
    val transactionData: TransactionData,
    val additionalInfo: AdditionalInfo? = null
) {
    sealed class AdditionalInfo {
        class Send(val info: SendInfo) : AdditionalInfo()

        val sendInfo: SendInfo?
            get() = (this as? Send)?.info
    }

    data class SendInfo(
        val nftShortMeta: NftShortMeta? = null
    )

    data class NftShortMeta(
        val nftName: String,
        val previewImageUrl: String?
    )
}

object SendEvmModule {

    data class TransactionDataParcelable(
        val toAddress: String,
        val value: BigInteger,
        val input: ByteArray
    ) {
        constructor(transactionData: TransactionData) : this(
            transactionData.to.hex,
            transactionData.value,
            transactionData.input
        )
    }


}

internal fun Scope.sendEvmViewModel(
    wallet: Wallet,
    address: Address?,
    hideAddress: Boolean,
    adapter: ISendEthereumAdapter
): SendEvmViewModel {
    val adapterManager: IAdapterManager = get()
    val amountValidator = AmountValidator()
    val coinMaxAllowedDecimals = wallet.token.decimals

    val availableBalance = adapterManager.getMaxSendableBalance(wallet, adapter.maxSpendableBalance)
    val amountService = SendAmountService(
        amountValidator,
        wallet.token.coin.code,
        availableBalance.setScale(coinMaxAllowedDecimals, RoundingMode.DOWN),
        wallet.token.type.isNative
    )
    val addressService = SendEvmAddressService()
    val xRateService = XRateService(get(), get<CurrencyManager>().baseCurrency)

    val sendTransactionService = SendTransactionServiceEvm(
        wallet.token,
        ignoreMevErrors = true,
        evmKitWrapper = adapter.evmTransactionRepository.evmKitWrapper
    )

    return SendEvmViewModel(
        wallet = wallet,
        sendToken = wallet.token,
        adapter = adapter,
        sendTransactionService = sendTransactionService,
        xRateService = xRateService,
        amountService = amountService,
        addressService = addressService,
        coinMaxAllowedDecimals = coinMaxAllowedDecimals,
        showAddressInput = !hideAddress,
        address = address,
        adapterManager = adapterManager,
        dispatcherProvider = get(),
    )
}
