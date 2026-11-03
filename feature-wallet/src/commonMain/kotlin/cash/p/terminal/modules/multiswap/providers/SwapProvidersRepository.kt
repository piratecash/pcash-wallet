package cash.p.terminal.modules.multiswap.providers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SwapProvidersRepository(
    private val storage: SwapProvidersStorage,
) {
    private val _disabledIds = MutableStateFlow(loadDisabledIds())
    val disabledIds: StateFlow<Set<String>> = _disabledIds.asStateFlow()

    fun isDisabled(providerId: String): Boolean =
        providerId in _disabledIds.value

    fun isMandatory(providerId: String): Boolean =
        providerId in MANDATORY_IDS

    fun disable(providerId: String) {
        if (isMandatory(providerId)) return
        mutateAndSave { it + providerId }
    }

    fun enable(providerId: String) {
        mutateAndSave { it - providerId }
    }

    fun setDisabled(providerId: String, disabled: Boolean) {
        if (disabled) disable(providerId) else enable(providerId)
    }

    private fun mutateAndSave(transform: (Set<String>) -> Set<String>) {
        val before = _disabledIds.value
        val after = transform(before)
        if (after == before) return
        _disabledIds.update { after }
        storage.disabledProviderIds = after
    }

    private fun loadDisabledIds(): Set<String> = storage.disabledProviderIds

    companion object {
        val MANDATORY_IDS: Set<String> = setOf(PancakeSwapProvider.id, PancakeSwapV3Provider.id)
    }
}
