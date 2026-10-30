package cash.p.terminal.modules.send.solana

import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.ISendSolanaAdapter
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
import io.horizontalsystems.solanakit.SolanaKit
import java.math.BigDecimal
import java.math.RoundingMode
import io.horizontalsystems.core.CurrencyManager
import org.koin.core.scope.Scope
import cash.p.terminal.core.ICoinManager

internal fun Scope.sendSolanaViewModel(
    wallet: Wallet,
    address: Address?,
    hideAddress: Boolean,
    adapter: ISendSolanaAdapter
): SendSolanaViewModel {
    val adapterManager: IAdapterManager = get()
    val amountValidator = AmountValidator()
    val coinMaxAllowedDecimals = wallet.token.decimals

    val availableBalance = adapterManager.getMaxSendableBalance(wallet, adapter.maxSpendableBalance)
    val amountService = SendAmountService(
        amountValidator,
        wallet.token.coin.code,
        availableBalance.setScale(
            coinMaxAllowedDecimals,
            RoundingMode.DOWN
        ),
        wallet.token.type.isNative,
    )
    val solToken = get<ICoinManager>().getToken(
        TokenQuery(
            BlockchainType.Solana,
            TokenType.Native
        )
    ) ?: throw IllegalArgumentException()
    val solBalance = (adapterManager.getAdjustedBalanceDataForToken(solToken)?.available
        ?: BigDecimal.ZERO) - SolanaKit.accountRentAmount
    val addressService = SendSolanaAddressService()
    val xRateService = XRateService(get(), get<CurrencyManager>().baseCurrency)

    return SendSolanaViewModel(
        wallet = wallet,
        sendToken = wallet.token,
        feeToken = solToken,
        solBalance = solBalance,
        adapter = adapter,
        xRateService = xRateService,
        amountService = amountService,
        addressService = addressService,
        coinMaxAllowedDecimals = coinMaxAllowedDecimals,
        showAddressInput = !hideAddress,
        address = address,
        contactsRepo = get(),
        connectivityManager = get(),
        pendingRegistrar = get(),
        adapterManager = adapterManager,
        dispatcherProvider = get(),
        recentAddressManager = get(),
        offlineTransactionPayloadEncoder = get(),
        offlineSignedTransactionRepository = get(),
    )
}

object SendSolanaModule {

    data class SendUiState(
        val availableBalance: BigDecimal,
        val amountCaution: HSCaution?,
        val addressError: Throwable?,
        val canBeSend: Boolean,
        val showAddressInput: Boolean,
        val address: Address?,
        val isPoisonAddress: Boolean = false,
        val riskAccepted: Boolean = false,
    )
}
