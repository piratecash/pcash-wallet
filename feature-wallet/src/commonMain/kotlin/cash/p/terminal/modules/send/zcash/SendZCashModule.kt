package cash.p.terminal.modules.send.zcash

import cash.p.terminal.core.ISendZcashAdapter
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.amount.AmountValidator
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.CurrencyManager
import org.koin.core.scope.Scope

internal fun Scope.sendZCashViewModel(
    wallet: Wallet,
    address: Address?,
    hideAddress: Boolean,
    adapter: ISendZcashAdapter
): SendZCashViewModel {
    val xRateService = XRateService(get(), get<CurrencyManager>().baseCurrency)
    val availableBalance = adapter.maxSpendableBalance
    val amountService = SendAmountService(
        amountValidator = AmountValidator(),
        coinCode = wallet.coin.code,
        availableBalance = availableBalance
    )
    val addressService = SendZCashAddressService(adapter)
    val memoService = SendZCashMemoService(
        memoSupportedByAccount = wallet.account.type !is AccountType.TrezorDevice
    )

    return SendZCashViewModel(
        adapter = adapter,
        wallet = wallet,
        xRateService = xRateService,
        amountService = amountService,
        addressService = addressService,
        memoService = memoService,
        contactsRepo = get(),
        showAddressInput = !hideAddress,
        address = address,
        pendingRegistrar = get(),
        adapterManager = get(),
        dispatcherProvider = get(),
        offlineTransactionPayloadEncoder = get(),
        offlineSignedTransactionRepository = get(),
    )
}
