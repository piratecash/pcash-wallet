package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.core.App
import cash.p.terminal.core.adapters.Eip20Adapter
import cash.p.terminal.modules.multiswap.action.ISwapProviderAction
import cash.p.terminal.wallet.Token
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.DefaultBlockParameter
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.useCases.WalletUseCase
import kotlinx.coroutines.rx2.await
import org.koin.java.KoinJavaComponent.inject
import java.math.BigDecimal
import kotlin.getValue

abstract class EvmSwapProvider : IMultiSwapProvider {
    override val walletUseCase: WalletUseCase by inject(WalletUseCase::class.java)

    protected suspend fun getAllowance(token: Token, spenderAddress: Address): BigDecimal? {
        if (token.type !is TokenType.Eip20) return null

        val eip20Adapter = App.adapterManager.getAdapterForToken<Eip20Adapter>(token) ?: return null

        return eip20Adapter.allowance(spenderAddress, DefaultBlockParameter.Latest).await()
    }

    protected suspend fun actionApprove(
        allowance: BigDecimal?,
        amountIn: BigDecimal,
        routerAddress: Address,
        token: Token,
    ): ISwapProviderAction? = EvmSwapHelper.actionApprove(allowance, amountIn, routerAddress, token)

}
