package cash.p.terminal.modules.send.ton

import cash.p.terminal.core.ISendTonAdapter
import cash.p.terminal.core.isNative
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.amount.AmountValidator
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.getMaxSendableBalance
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.CurrencyManager
import org.koin.core.scope.Scope
import cash.p.terminal.core.ICoinManager

internal fun Scope.sendTonViewModel(
    wallet: Wallet,
    address: Address?,
    hideAddress: Boolean,
    adapter: ISendTonAdapter
): SendTonViewModel {
    val adapterManager: IAdapterManager = get()
    val amountValidator = AmountValidator()
    val coinMaxAllowedDecimals = wallet.token.decimals

    val availableBalance = adapterManager.getMaxSendableBalance(wallet, adapter.maxSpendableBalance)
    val amountService = SendTonAmountService(
        amountValidator = amountValidator,
        coinCode = wallet.coin.code,
        availableBalance = availableBalance,
        leaveSomeBalanceForFee = wallet.token.type.isNative
    )
    val addressService = SendTonAddressService()
    val feeService = SendTonFeeService(adapter)
    val xRateService = XRateService(get(), get<CurrencyManager>().baseCurrency)
    val feeToken = get<ICoinManager>().getToken(TokenQuery(BlockchainType.Ton, TokenType.Native))
        ?: throw IllegalArgumentException()

    return SendTonViewModel(
        wallet = wallet,
        sendToken = wallet.token,
        feeToken = feeToken,
        adapter = adapter,
        xRateService = xRateService,
        amountService = amountService,
        addressService = addressService,
        feeService = feeService,
        coinMaxAllowedDecimals = coinMaxAllowedDecimals,
        contactsRepo = get(),
        showAddressInput = !hideAddress,
        address = address,
        pendingRegistrar = get(),
        adapterManager = adapterManager,
        dispatcherProvider = get(),
        recentAddressManager = get(),
        offlineTransactionPayloadEncoder = get(),
        offlineSignedTransactionRepository = get(),
        anchorService = TonOfflineAnchorService(adapter, get()),
    )
}
