package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.entities.UnstoppableProviderTitles

class AndroidUnstoppableProviderTitles : UnstoppableProviderTitles {
    override fun title(apiId: String?): String? = UnstoppableProvider.displayTitle(apiId)
}
