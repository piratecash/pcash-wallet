package cash.p.terminal.modules.send.monero

import cash.p.terminal.core.ISendMoneroAdapter
import cash.p.terminal.core.isNative
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.amount.AmountValidator
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.getMaxSendableBalance
import java.math.RoundingMode
import io.horizontalsystems.core.CurrencyManager
import org.koin.core.scope.Scope

internal fun Scope.sendMoneroViewModel(
    wallet: Wallet,
    address: Address?,
    hideAddress: Boolean,
    adapter: ISendMoneroAdapter
): SendMoneroViewModel {
    val adapterManager: IAdapterManager = get()
    val amountValidator = AmountValidator()
    val coinMaxAllowedDecimals = wallet.token.decimals

    val availableBalance = adapterManager.getMaxSendableBalance(wallet, adapter.maxSpendableBalance)
    val amountService = SendAmountService(
        amountValidator,
        wallet.token.coin.code,
        availableBalance.setScale(coinMaxAllowedDecimals, RoundingMode.DOWN),
        wallet.token.type.isNative,
    )
    val addressService = SendMoneroAddressService()
    val xRateService = XRateService(get(), get<CurrencyManager>().baseCurrency)

    return SendMoneroViewModel(
        wallet = wallet,
        sendToken = wallet.token,
        adapter = adapter,
        xRateService = xRateService,
        amountService = amountService,
        addressService = addressService,
        coinMaxAllowedDecimals = coinMaxAllowedDecimals,
        showAddressInput = !hideAddress,
        contactsRepo = get(),
        connectivityManager = get(),
        address = address,
        adapterManager = adapterManager,
        dispatcherProvider = get(),
        offlineTransactionPayloadEncoder = get(),
        offlineSignedTransactionRepository = get(),
    )
}
