package cash.p.terminal.modules.evmfee

import cash.p.terminal.data.repository.Eip1559FeeHistory
import cash.p.terminal.data.repository.Eip1559FeeHistory.BLOCKS_COUNT
import cash.p.terminal.data.repository.Eip1559FeeHistory.REWARD_PERCENTILE
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.core.LegacyGasPriceProvider
import io.horizontalsystems.ethereumkit.core.eip1559.Eip1559GasPriceProvider
import io.horizontalsystems.ethereumkit.core.rollup.L1FeeProvider
import io.horizontalsystems.ethereumkit.models.DefaultBlockParameter
import io.horizontalsystems.ethereumkit.models.GasPrice
import io.horizontalsystems.ethereumkit.models.TransactionData
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.models.Address
import io.reactivex.Single
import kotlinx.coroutines.rx2.await
import java.math.BigInteger

open class EvmCommonGasDataService(
    private val evmKit: EthereumKit,
    protected val predefinedGasLimit: Long? = null
) {

    open fun estimatedGasDataAsync(
        gasPrice: GasPrice, transactionData: TransactionData, stubAmount: BigInteger? = null, toAddress: Address? = null
    ): Single<GasData> {
        if (predefinedGasLimit != null) {
            return Single.just(GasData(gasLimit = predefinedGasLimit, gasPrice = gasPrice))
        }

        val surchargeRequired = transactionData.input.isNotEmpty()

        val stubTransactionData = if (stubAmount != null) {
            TransactionData(transactionData.to, BigInteger.ONE, transactionData.input)
        } else {
            transactionData
        }

        return if (toAddress != null) {
            evmKit.estimateGas(
                to = toAddress,
                value = transactionData.value,
                gasPrice = gasPrice
            )
                .onErrorResumeNext {
                    evmKit.estimateGas(
                        to = toAddress,
                        value = stubTransactionData.value,
                        gasPrice = null,
                        data = null
                    )
                }
                .map { estimatedGasLimit ->
                    val gasLimit =
                        if (surchargeRequired) EvmFeeModule.surcharged(estimatedGasLimit) else estimatedGasLimit
                    GasData(
                        gasLimit = gasLimit,
                        estimatedGasLimit = estimatedGasLimit,
                        gasPrice = gasPrice
                    )
                }
        } else {
            evmKit.estimateGas(stubTransactionData, gasPrice)
                .onErrorResumeNext {
                    evmKit.estimateGas(stubTransactionData)
                }
                .map { estimatedGasLimit ->
                    val gasLimit =
                        if (surchargeRequired) EvmFeeModule.surcharged(estimatedGasLimit) else estimatedGasLimit
                    GasData(
                        gasLimit = gasLimit,
                        estimatedGasLimit = estimatedGasLimit,
                        gasPrice = gasPrice
                    )
                }
        }
    }

    companion object {
        fun instance(
            evmKit: EthereumKit, blockchainType: BlockchainType, gasLimit: Long? = null
        ): EvmCommonGasDataService {
            val l1FeeContractAddress = blockchainType.l1GasFeeContractAddress

            return if (l1FeeContractAddress == null) {
                EvmCommonGasDataService(evmKit, gasLimit)
            } else {
                val l1FeeProvider = L1FeeProvider(evmKit, l1FeeContractAddress)
                EvmRollupGasDataService(evmKit, l1FeeProvider, gasLimit)
            }
        }
    }

}

/** Priced and estimated like the send confirmation's full-balance transfer, so "100%" passes its balance check. */
suspend fun estimateNativeTransferFee(evmKit: EthereumKit, blockchainType: BlockchainType): BigInteger {
    val gasPrice = if (evmKit.chain.isEIP1559Supported) {
        val feeHistory = Eip1559GasPriceProvider(evmKit)
            .feeHistorySingle(BLOCKS_COUNT, DefaultBlockParameter.Latest, REWARD_PERCENTILE).await()
        Eip1559FeeHistory.recommendedGasPrice(feeHistory)
    } else {
        GasPrice.Legacy(LegacyGasPriceProvider(evmKit).gasPriceSingle().await())
    }
    val balance = evmKit.accountState?.balance ?: BigInteger.ZERO
    val fullBalanceTransfer = TransactionData(evmKit.receiveAddress, balance, byteArrayOf())
    return EvmCommonGasDataService.instance(evmKit, blockchainType)
        .estimatedGasDataAsync(gasPrice, fullBalanceTransfer, stubAmount = BigInteger.ONE)
        .await()
        .fee
}
