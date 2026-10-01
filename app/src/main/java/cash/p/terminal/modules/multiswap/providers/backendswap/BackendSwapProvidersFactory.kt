package cash.p.terminal.modules.multiswap.providers.backendswap

import cash.p.terminal.modules.multiswap.providers.OffChainSwapProviderSupport
import cash.p.terminal.network.backendswap.data.repository.BackendSwapRepository
import cash.p.terminal.network.backendswap.domain.entity.BackendSwapProviderInfo
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.useCases.WalletUseCase
import io.horizontalsystems.core.DispatcherProvider

/** Reuses a provider instance while its backend info is unchanged, so its order cache survives list refreshes. */
class BackendSwapProvidersFactory(
    private val walletUseCase: WalletUseCase,
    private val accountManager: IAccountManager,
    private val backendSwapRepository: BackendSwapRepository,
    private val assetResolver: BackendSwapAssetResolver,
    private val signer: BackendSwapSigner,
    private val providerSupport: OffChainSwapProviderSupport,
    private val dispatcherProvider: DispatcherProvider,
) {
    @Volatile
    private var providersByName: Map<String, BackendSwapProvider> = emptyMap()

    fun providers(infos: List<BackendSwapProviderInfo>): List<BackendSwapProvider> {
        val known = providersByName
        val providers = infos.map { info -> known[info.name]?.takeIf { it.info == info } ?: create(info) }
        providersByName = providers.associateBy { it.info.name }
        return providers
    }

    private fun create(info: BackendSwapProviderInfo) = BackendSwapProvider(
        info = info,
        walletUseCase = walletUseCase,
        accountManager = accountManager,
        backendSwapRepository = backendSwapRepository,
        assetResolver = assetResolver,
        signer = signer,
        providerSupport = providerSupport,
        dispatcherProvider = dispatcherProvider,
    )
}
