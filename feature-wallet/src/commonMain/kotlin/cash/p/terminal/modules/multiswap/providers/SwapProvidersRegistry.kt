package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.modules.multiswap.providers.backendswap.BackendSwapProvidersFactory
import cash.p.terminal.modules.multiswap.providers.backendswap.BackendSwapProvidersRepository
import cash.p.terminal.modules.paycore.PayCoreProvider

class SwapProvidersRegistry(
    changeNowProvider: ChangeNowProvider,
    quickexProvider: QuickexProvider,
    exolixProvider: ExolixProvider,
    yiFiProvider: YiFiProvider,
    stonFiProvider: StonFiProvider,
    payCoreProvider: PayCoreProvider,
    unstoppableProvidersFactory: UnstoppableProvidersFactory,
    private val backendSwapProvidersRepository: BackendSwapProvidersRepository,
    private val backendSwapProvidersFactory: BackendSwapProvidersFactory,
) {
    private val staticProviders: List<IMultiSwapProvider> = listOf(
        OneInchProvider,
        PancakeSwapProvider,
        PancakeSwapV3Provider,
        QuickSwapProvider,
        UniswapProvider,
        UniswapV3Provider,
        changeNowProvider,
        quickexProvider,
        exolixProvider,
        yiFiProvider,
        ThorChainProvider,
        MayaProvider,
        AllBridgeProvider,
        stonFiProvider,
        payCoreProvider
    ) + unstoppableProvidersFactory.create()

    val providers: List<IMultiSwapProvider>
        get() = staticProviders + backendSwapProvidersFactory.providers(backendSwapProvidersRepository.providers.value)

    fun findById(id: String): IMultiSwapProvider? =
        providers.firstOrNull { it.id == id }
}
