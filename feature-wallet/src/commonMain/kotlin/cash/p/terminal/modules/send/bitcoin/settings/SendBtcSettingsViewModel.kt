package cash.p.terminal.modules.send.bitcoin.settings

import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.HSCaution
import cash.p.terminal.core.adapters.BitcoinFeeInfo
import cash.p.terminal.core.providers.SharedAppConfig
import cash.p.terminal.modules.send.bitcoin.SendBitcoinFeeRateService
import cash.p.terminal.modules.send.bitcoin.SendBitcoinFeeService
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.CurrencyValue
import kotlinx.coroutines.launch
import java.math.BigDecimal
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.CurrencyManager

class SendBtcSettingsViewModel(
    private val feeRateService: SendBitcoinFeeRateService,
    private val feeService: SendBitcoinFeeService,
    val token: Token
) : ViewModelUiState<SendBtcSettingsUiState>() {
    val coinMaxAllowedDecimals = token.decimals
    val fiatMaxAllowedDecimals = SharedAppConfig.fiatDecimal

    private var feeRateState = feeRateService.stateFlow.value
    private var bitcoinFeeInfo = feeService.bitcoinFeeInfoFlow.value
    val feeRateChangeable by feeRateService::feeRateChangeable

    private val baseCurrency = getKoinInstance<CurrencyManager>().baseCurrency
    private val rate = getKoinInstance<MarketKitWrapper>().coinPrice(token.coin.uid, baseCurrency.code)?.let {
        CurrencyValue(baseCurrency, it.value)
    }

    init {
        viewModelScope.launch {
            feeRateService.stateFlow.collect {
                handleFeeRateState(it)
            }
        }

        viewModelScope.launch {
            feeService.bitcoinFeeInfoFlow.collect {
                handleBitcoinFeeInfo(it)
            }
        }
    }

    private fun handleFeeRateState(state: SendBitcoinFeeRateService.State) {
        feeRateState = state

        feeService.setFeeRate(feeRateState.feeRate)

        emitState()
    }

    private fun handleBitcoinFeeInfo(info: BitcoinFeeInfo?) {
        bitcoinFeeInfo = info

        emitState()
    }

    override fun createState() = SendBtcSettingsUiState(
        resetEnabled = !feeRateState.isRecommended,
        feeRate = feeRateState.feeRate,
        feeRateCaution = feeRateState.feeRateCaution,
        fee = bitcoinFeeInfo?.fee,
        rate = rate
    )

    fun reset() {
        feeRateService.reset()
    }

    fun updateFeeRate(v: Int) {
        feeRateService.setFeeRate(v)
    }

    fun incrementFeeRate() {
        val incremented = (feeRateState.feeRate ?: 0) + 1
        updateFeeRate(incremented)
    }

    fun decrementFeeRate() {
        var incremented = (feeRateState.feeRate ?: 0) - 1
        if (incremented < 0) {
            incremented = 0
        }
        updateFeeRate(incremented)
    }
}

data class SendBtcSettingsUiState(
    val resetEnabled: Boolean,
    val feeRate: Int?,
    val feeRateCaution: HSCaution?,
    val fee: BigDecimal?,
    val rate: CurrencyValue?,
)
