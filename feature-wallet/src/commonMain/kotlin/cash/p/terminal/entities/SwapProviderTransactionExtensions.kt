package cash.p.terminal.entities

import cash.p.terminal.modules.multiswap.providers.backendswap.BackendSwapBranding
import cash.p.terminal.network.backendswap.api.BackendSwapHelper
import cash.p.terminal.network.changenow.api.ChangeNowHelper
import cash.p.terminal.network.exolix.api.ExolixHelper
import cash.p.terminal.network.quickex.api.QuickexHelper
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.network.yifi.api.YiFiHelper

fun SwapProviderTransaction.toStatusUrl(): Pair<String, String>? = when (provider) {
    SwapProvider.CHANGENOW -> ChangeNowHelper.CHANGE_NOW_URL to ChangeNowHelper.getViewTransactionUrl(transactionId)
    SwapProvider.QUICKEX -> QuickexHelper.QUICKEX_URL to QuickexHelper.getViewTransactionUrl(
        transactionId, addressOut
    )

    SwapProvider.EXOLIX -> ExolixHelper.EXOLIX_URL to ExolixHelper.getViewTransactionUrl(transactionId)
    SwapProvider.YIFI -> YiFiHelper.YIFI_URL to YiFiHelper.getViewTransactionUrl(
        transactionId, addressOut
    )
    SwapProvider.PCASH_BACKEND -> BackendSwapHelper.trackUrl(subProviderId, providerExternalId)
    SwapProvider.PAYCORE,
    SwapProvider.THORCHAIN,
    SwapProvider.MAYA,
    SwapProvider.UNSTOPPABLE -> null
}

/** Unstoppable sub-providers are listed by the swap module. */
interface UnstoppableProviderTitles {
    /** Falls back to [apiId] for a sub-provider the platform does not list. */
    fun title(apiId: String?): String?
}

fun UnstoppableProviderTitles.swapProviderDisplayTitle(provider: SwapProvider, subProviderId: String?): String = when {
    provider == SwapProvider.UNSTOPPABLE -> title(subProviderId) ?: provider.title
    provider == SwapProvider.YIFI && !subProviderId.isNullOrBlank() -> "${provider.title} · $subProviderId"
    provider == SwapProvider.PCASH_BACKEND -> BackendSwapBranding.title(subProviderId.orEmpty())
    else -> provider.title
}
