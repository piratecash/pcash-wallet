package cash.p.terminal.core.providers

import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.ZcashBirthdayProvider
import cash.p.terminal.core.usecase.ValidateMoneroHeightUseCase
import cash.p.terminal.wallet.Account
import io.horizontalsystems.core.entities.BlockchainType

class PredefinedBlockchainSettingsProvider(
    private val manager: RestoreSettingsManager,
    private val zcashBirthdayProvider: ZcashBirthdayProvider,
    private val validateMoneroHeightUseCase: ValidateMoneroHeightUseCase,
    private val zcashDbMarker: ZcashDbMarker,
) {

    fun prepareNew(account: Account, blockchainType: BlockchainType) {
        val settings = RestoreSettings()
        when (blockchainType) {
            BlockchainType.Zcash -> {
                settings.birthdayHeight = zcashBirthdayProvider.getLatestCheckpointBlockHeight()
                zcashDbMarker.markPristine(account.id)
            }
            BlockchainType.Monero -> {
                settings.birthdayHeight = validateMoneroHeightUseCase.getTodayHeight()
            }
            else -> {}
        }
        if (settings.isNotEmpty()) {
            manager.save(settings, account, blockchainType)
        }
    }
}
