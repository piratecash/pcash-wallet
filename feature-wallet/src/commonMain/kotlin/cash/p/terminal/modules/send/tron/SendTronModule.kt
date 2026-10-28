package cash.p.terminal.modules.send.tron

import cash.p.terminal.core.ISendTronAdapter
import cash.p.terminal.core.isNative
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.amount.AmountValidator
import cash.p.terminal.modules.amount.SendAmountService
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.getMaxSendableBalance
import io.horizontalsystems.core.entities.BlockchainType
import java.math.RoundingMode
import io.horizontalsystems.core.CurrencyManager
import org.koin.core.scope.Scope
import cash.p.terminal.core.ICoinManager

internal fun Scope.sendTronViewModel(
    wallet: Wallet,
    address: Address?,
    hideAddress: Boolean,
    adapter: ISendTronAdapter
): SendTronViewModel {
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
    val addressService = SendTronAddressService(adapter, wallet.token)
    val xRateService = XRateService(get(), get<CurrencyManager>().baseCurrency)
    val feeToken = get<ICoinManager>().getToken(TokenQuery(BlockchainType.Tron, TokenType.Native))
        ?: throw IllegalArgumentException()

    return SendTronViewModel(
        wallet = wallet,
        sendToken = wallet.token,
        feeToken = feeToken,
        adapter = adapter,
        xRateService = xRateService,
        amountService = amountService,
        addressService = addressService,
        coinMaxAllowedDecimals = coinMaxAllowedDecimals,
        contactsRepo = get(),
        showAddressInput = !hideAddress,
        address = address,
        adapterManager = adapterManager,
        dispatcherProvider = get(),
        recentAddressManager = get(),
        offlineTransactionPayloadEncoder = get(),
        offlineSignedTransactionRepository = get(),
    )
}
