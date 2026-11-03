package cash.p.terminal.modules.evmfee.legacy

import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.modules.evmfee.Bound
import cash.p.terminal.modules.evmfee.FeeSettingsWarning
import cash.p.terminal.modules.evmfee.GasPriceInfo
import cash.p.terminal.modules.evmfee.IEvmGasPriceService
import io.horizontalsystems.ethereumkit.core.LegacyGasPriceProvider
import io.horizontalsystems.ethereumkit.models.GasPrice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.await
import java.math.BigDecimal

class LegacyGasPriceService(
    private val gasPriceProvider: LegacyGasPriceProvider,
    private val minRecommendedGasPrice: Long? = null,
    private val initialGasPrice: Long? = null,
) : IEvmGasPriceService() {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var setGasPriceJob: Job? = null
    private var recommendedGasPrice: Long? = null
    private var lastRequestedGasPrice: Long? = null

    private val overpricingBound = Bound.Multiplied(BigDecimal(1.5))
    private val riskOfStuckBound = Bound.Multiplied(BigDecimal(0.9))

    private var state: DataState<GasPriceInfo> = DataState.Loading

    override fun createState() = state

    override fun start() {
        if (initialGasPrice != null) {
            setGasPrice(initialGasPrice)
        } else {
            setRecommended()
        }
    }

    private suspend fun getRecommendedGasPriceSingle(): Long {
        recommendedGasPrice?.let {
            return it
        }

        val gasPrice = gasPriceProvider.gasPriceSingle().await()
        val adjustedGasPrice = gasPrice.coerceAtLeast(minRecommendedGasPrice ?: 0)

        recommendedGasPrice = adjustedGasPrice

        return adjustedGasPrice
    }

    override fun setRecommended() {
        setGasPriceInternal(null)
    }

    fun setGasPrice(value: Long) {
        setGasPriceInternal(value)
    }

    override fun retry() {
        if (state is DataState.Error) setGasPriceInternal(lastRequestedGasPrice)
    }

    private fun setGasPriceInternal(value: Long?) {
        lastRequestedGasPrice = value
        state = DataState.Loading
        emitState()

        setGasPriceJob?.cancel()
        setGasPriceJob = coroutineScope.launch {
            try {
                val recommended = getRecommendedGasPriceSingle()

                val gasPriceInfo = if (value == null) {
                    GasPriceInfo(
                        gasPrice = GasPrice.Legacy(recommended),
                        gasPriceDefault = GasPrice.Legacy(recommended),
                        default = true,
                        warnings = listOf<FeeSettingsWarning>(),
                        errors = listOf()
                    )
                } else {
                    val warnings = buildList {
                        if (value < riskOfStuckBound.calculate(recommended)) {
                            add(FeeSettingsWarning.RiskOfGettingStuckLegacy)
                        }

                        if (value >= overpricingBound.calculate(recommended)) {
                            add(FeeSettingsWarning.Overpricing)
                        }
                    }

                    GasPriceInfo(
                        gasPrice = GasPrice.Legacy(value),
                        gasPriceDefault = GasPrice.Legacy(recommended),
                        default = false,
                        warnings = warnings,
                        errors = listOf()
                    )
                }

                state = DataState.Success(gasPriceInfo)
                emitState()
            } catch (e: CancellationException) {
                // A superseded request must not show an error that offers a retry.
                throw e
            } catch (e: Throwable) {
                state = DataState.Error(e)
                emitState()
            }
        }
    }
}
