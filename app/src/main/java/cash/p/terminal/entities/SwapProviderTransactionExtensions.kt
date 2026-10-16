package cash.p.terminal.entities

import cash.p.terminal.R
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.modules.multiswap.providers.UnstoppableProvider
import cash.p.terminal.modules.multiswap.providers.backendswap.BackendSwapBranding
import cash.p.terminal.network.backendswap.api.BackendSwapHelper
import cash.p.terminal.network.changenow.api.ChangeNowHelper
import cash.p.terminal.network.exolix.api.ExolixHelper
import cash.p.terminal.network.quickex.api.QuickexHelper
import cash.p.terminal.network.swaprepository.SwapProvider
import cash.p.terminal.network.yifi.api.YiFiHelper
import cash.p.terminal.strings.helpers.Translator

fun SwapProviderTransaction.toStatusUrl(): Pair<String, String>? = when (provider) {
    SwapProvider.CHANGENOW -> ChangeNowHelper.CHANGE_NOW_URL to ChangeNowHelper.getViewTransactionUrl(transactionId)
    SwapProvider.QUICKEX -> QuickexHelper.QUICKEX_URL to QuickexHelper.getViewTransactionUrl(
        transactionId, addressOut
    )

    SwapProvider.PAYCORE -> Translator.getString(R.string.paycore_support) to AppConfigProvider.payCoreSupportUrl
    SwapProvider.EXOLIX -> ExolixHelper.EXOLIX_URL to ExolixHelper.getViewTransactionUrl(transactionId)
    SwapProvider.YIFI -> YiFiHelper.YIFI_URL to YiFiHelper.getViewTransactionUrl(
        transactionId, addressOut
    )
    SwapProvider.PCASH_BACKEND -> BackendSwapHelper.trackUrl(subProviderId, providerExternalId)
    SwapProvider.THORCHAIN,
    SwapProvider.MAYA,
    SwapProvider.UNSTOPPABLE -> null
}

fun swapProviderDisplayTitle(provider: SwapProvider, subProviderId: String?): String = when {
    provider == SwapProvider.UNSTOPPABLE -> UnstoppableProvider.displayTitle(subProviderId) ?: provider.title
    provider == SwapProvider.YIFI && !subProviderId.isNullOrBlank() -> "${provider.title} · $subProviderId"
    provider == SwapProvider.PCASH_BACKEND -> BackendSwapBranding.title(subProviderId.orEmpty())
    else -> provider.title
}
