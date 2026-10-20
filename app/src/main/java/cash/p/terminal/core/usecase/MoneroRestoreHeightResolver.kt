package cash.p.terminal.core.usecase

import cash.p.terminal.trezor.domain.usecase.TrezorMoneroRestoreHeightResolver

class MoneroRestoreHeightResolver(
    private val validateMoneroHeightUseCase: ValidateMoneroHeightUseCase,
) : TrezorMoneroRestoreHeightResolver {
    override fun getTodayHeight(): Long = validateMoneroHeightUseCase.getTodayHeight()

    override fun resolve(value: String): Long = validateMoneroHeightUseCase(value)
}
