package cash.p.terminal.core.ethereum

import cash.p.terminal.core.EvmError
import cash.p.terminal.wallet.Warning
import cash.p.terminal.core.convertedError
import cash.p.terminal.modules.evmfee.CalculateWarning
import cash.p.terminal.modules.evmfee.FeeSettingsError
import cash.p.terminal.modules.evmfee.FeeSettingsWarning
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.IAppNumberFormatter
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Error_InsufficientBalanceForFee
import cash.p.terminal.resources.EthereumTransaction_Error_CannotEstimate
import cash.p.terminal.resources.EthereumTransaction_Error_CannotEstimate_Title
import cash.p.terminal.resources.EthereumTransaction_Error_ExecutionReverted
import cash.p.terminal.resources.EthereumTransaction_Error_InsufficientBalanceWithFee
import cash.p.terminal.resources.EthereumTransaction_Error_InsufficientBalance_Title
import cash.p.terminal.resources.EthereumTransaction_Error_InsufficientLiquidity
import cash.p.terminal.resources.EthereumTransaction_Error_InsufficientLiquidity_Title
import cash.p.terminal.resources.EthereumTransaction_Error_LowerThanBaseGasLimit
import cash.p.terminal.resources.EthereumTransaction_Error_LowerThanBaseGasLimit_Title
import cash.p.terminal.resources.EthereumTransaction_Error_Title
import cash.p.terminal.resources.EthereumTransaction_Warning_Title
import cash.p.terminal.resources.FeeSettings_Overpricing
import cash.p.terminal.resources.FeeSettings_Overpricing_Title
import cash.p.terminal.resources.FeeSettings_RiskOfGettingStuck
import cash.p.terminal.resources.FeeSettings_RiskOfGettingStuckLegacy
import cash.p.terminal.resources.FeeSettings_RiskOfGettingStuckLegacy_Title
import cash.p.terminal.resources.FeeSettings_RiskOfGettingStuck_Title
import cash.p.terminal.resources.SendEvmSettings_Error_NonceUsed
import cash.p.terminal.resources.SendEvmSettings_Error_NonceUsed_Title
import cash.p.terminal.resources.source_blocked_by_provider_error
import cash.p.terminal.resources.warning_inaccurate_commission

class CautionViewItemFactory(
    private val baseCoinService: EvmCoinService,
    private val numberFormatter: IAppNumberFormatter
) {
    fun cautionViewItems(warnings: List<Warning>, errors: List<Throwable>): List<CautionViewItem> {
        return warnings.map { cautionViewItem(it) } + errors.map { cautionViewItem(it) }
    }

    private fun cautionViewItem(warning: Warning): CautionViewItem {
        return when (warning) {
            FeeSettingsWarning.RiskOfGettingStuck -> {
                CautionViewItem(
                    Translator.getString(Res.string.FeeSettings_RiskOfGettingStuck_Title),
                    Translator.getString(Res.string.FeeSettings_RiskOfGettingStuck),
                    CautionViewItem.Type.Warning
                )
            }
            FeeSettingsWarning.RiskOfGettingStuckLegacy -> {
                CautionViewItem(
                    Translator.getString(Res.string.FeeSettings_RiskOfGettingStuckLegacy_Title),
                    Translator.getString(Res.string.FeeSettings_RiskOfGettingStuckLegacy),
                    CautionViewItem.Type.Warning
                )
            }
            FeeSettingsWarning.Overpricing -> {
                CautionViewItem(
                    Translator.getString(Res.string.FeeSettings_Overpricing_Title),
                    Translator.getString(Res.string.FeeSettings_Overpricing),
                    CautionViewItem.Type.Warning
                )
            }

            CalculateWarning.Reverted -> {
                CautionViewItem(
                    Translator.getString(Res.string.EthereumTransaction_Warning_Title),
                    Translator.getString(Res.string.warning_inaccurate_commission),
                    CautionViewItem.Type.Warning
                )
            }

            else -> {
                CautionViewItem(
                    Translator.getString(Res.string.EthereumTransaction_Warning_Title),
                    warning.javaClass.simpleName,
                    CautionViewItem.Type.Warning
                )
            }
        }
    }

    private fun cautionViewItem(error: Throwable): CautionViewItem {
        return when (error) {
            is FeeSettingsError.InsufficientBalance -> {
                val balance = baseCoinService.convertToMonetaryValue(error.balance)
                val formattedBalance = numberFormatter.formatCoinFull(
                    balance,
                    null,
                    baseCoinService.token.decimals
                )
                CautionViewItem(
                    Translator.getString(Res.string.EthereumTransaction_Error_InsufficientBalance_Title),
                    Translator.getString(
                        Res.string.Error_InsufficientBalanceForFee,
                        baseCoinService.token.coin.code,
                        formattedBalance
                    ),
                    CautionViewItem.Type.Error
                )
            }
            FeeSettingsError.UsedNonce -> {
                CautionViewItem(
                    Translator.getString(Res.string.SendEvmSettings_Error_NonceUsed_Title),
                    Translator.getString(Res.string.SendEvmSettings_Error_NonceUsed),
                    CautionViewItem.Type.Error
                )
            }
            else -> {
                val (title, text) = convertError(error)
                CautionViewItem(title, text, CautionViewItem.Type.Error)
            }
        }
    }

    private fun convertError(error: Throwable): Pair<String, String> =
        when (val convertedError = error.convertedError) {
            is EvmError.InsufficientBalanceWithFee -> {
                Pair(
                    Translator.getString(Res.string.EthereumTransaction_Error_Title),
                    Translator.getString(
                        Res.string.EthereumTransaction_Error_InsufficientBalanceWithFee,
                        baseCoinService.token.coin.code
                    )
                )
            }
            is EvmError.ExecutionReverted -> {
                Pair(
                    Translator.getString(Res.string.EthereumTransaction_Error_Title),
                    Translator.getString(
                        Res.string.EthereumTransaction_Error_ExecutionReverted,
                        convertedError.message ?: ""
                    )
                )
            }
            is EvmError.CannotEstimateSwap -> {
                Pair(
                    Translator.getString(Res.string.EthereumTransaction_Error_CannotEstimate_Title),
                    Translator.getString(
                        Res.string.EthereumTransaction_Error_CannotEstimate,
                        baseCoinService.token.coin.code
                    )
                )
            }
            is EvmError.LowerThanBaseGasLimit -> {
                Pair(
                    Translator.getString(Res.string.EthereumTransaction_Error_LowerThanBaseGasLimit_Title),
                    Translator.getString(Res.string.EthereumTransaction_Error_LowerThanBaseGasLimit)
                )
            }
            is EvmError.InsufficientLiquidity -> {
                Pair(
                    Translator.getString(Res.string.EthereumTransaction_Error_InsufficientLiquidity_Title),
                    Translator.getString(Res.string.EthereumTransaction_Error_InsufficientLiquidity)
                )
            }
            is EvmError.BlockedByProvider -> {
                Pair(
                    Translator.getString(Res.string.EthereumTransaction_Error_Title),
                    Translator.getString(Res.string.source_blocked_by_provider_error)
                )
            }
            else -> {
                Pair(
                    Translator.getString(Res.string.EthereumTransaction_Error_Title),
                    convertedError.message ?: convertedError.javaClass.simpleName
                )
            }
        }
}
