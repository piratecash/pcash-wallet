package cash.p.terminal.modules.transactionInfo.resendbitcoin

import cash.p.terminal.core.adapters.BitcoinBaseAdapter
import cash.p.terminal.core.factories.FeeRateProviderFactory
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import cash.p.terminal.modules.transactionInfo.options.SpeedUpCancelType
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.transaction.TransactionSource
import cash.p.terminal.core.managers.TransactionAdapterManager
import io.horizontalsystems.core.CurrencyManager
import org.koin.core.scope.Scope

internal fun Scope.resendBitcoinViewModel(
    optionType: SpeedUpCancelType,
    transactionRecord: BitcoinTransactionRecord,
    source: TransactionSource
): ResendBitcoinViewModel {
    val adapter = get<TransactionAdapterManager>().getAdapter(source) as BitcoinBaseAdapter
    val replacementInfo = when (optionType) {
        SpeedUpCancelType.SpeedUp -> adapter.speedUpTransactionInfo(transactionRecord.transactionHash)
        // SpeedUpCancelType.Cancel -> adapter.cancelTransactionInfo(transactionRecord.transactionHash) // MOBILE-593
    }

    return ResendBitcoinViewModel(
        type = optionType,
        transactionRecord = transactionRecord,
        replacementInfo = replacementInfo,
        adapter = adapter,
        xRateService = XRateService(get(), get<CurrencyManager>().baseCurrency),
        feeRateProvider = FeeRateProviderFactory.provider(adapter.wallet.token.blockchainType)!!,
        contactsRepo = get(),
        offlineOperationGate = get()
    )
}
