package cash.p.terminal.modules.send.memo

import cash.p.terminal.core.ISendMemoAdapter
import cash.p.terminal.core.isNative
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.amount.AmountValidator
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.getMaxSendableBalance
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.CurrencyManager
import org.koin.core.scope.Scope
import cash.p.terminal.core.ICoinManager

internal fun Scope.sendMemoViewModel(
    wallet: Wallet,
    address: Address?,
    hideAddress: Boolean,
    adapter: ISendMemoAdapter
): SendMemoViewModel {
    val adapterManager: IAdapterManager = get()
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
    val xRateService = XRateService(get(), get<CurrencyManager>().baseCurrency)
    val feeToken = get<ICoinManager>().getToken(
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
        get(),
        SendMemoMinimumAmountService(adapter),
        adapterManager,
        get(),
        get(),
        get(),
        get(),
        chain,
        get(),
    )
}
