package cash.p.terminal.modules.send.evm.settings

import cash.p.terminal.core.ethereum.CautionViewItemFactory
import cash.p.terminal.core.ethereum.EvmCoinService
import cash.p.terminal.core.getKoinInstance

object SendEvmSettingsModule {

    fun viewModel(
        settingsService: SendEvmSettingsService,
        evmCoinService: EvmCoinService
    ) = SendEvmSettingsViewModel(
        settingsService,
        CautionViewItemFactory(evmCoinService, getKoinInstance())
    )
}
