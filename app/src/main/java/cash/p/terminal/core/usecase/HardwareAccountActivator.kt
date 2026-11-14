package cash.p.terminal.core.usecase

import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.monero
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext

/** Final step of creating a hardware wallet account: store its keys and switch to it. */
internal class HardwareAccountActivator(
    private val accountManager: IAccountManager,
    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
    private val walletActivator: WalletActivator,
    private val dispatcherProvider: DispatcherProvider,
) {
    suspend fun activate(
        account: Account,
        tokenQueries: List<TokenQuery>,
        publicKeys: List<HardwarePublicKey>,
    ) {
        withContext(dispatcherProvider.io) {
            hardwarePublicKeyStorage.save(publicKeys)
        }
        val activatableTokens = tokenQueries.filter { token ->
            token == TokenQuery.monero ||
                publicKeys.any {
                    it.blockchainType == token.blockchainType.uid &&
                        it.tokenType == token.tokenType
                }
        }
        walletActivator.activateWalletsSuspended(account, activatableTokens)
        accountManager.setActiveAccountId(account.id)
    }
}
