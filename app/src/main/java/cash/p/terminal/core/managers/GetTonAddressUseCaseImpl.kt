package cash.p.terminal.core.managers

import cash.p.terminal.feature.miniapp.domain.usecase.GetTonAddressUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.entities.TonWalletVersion
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.withContext

class GetTonAddressUseCaseImpl(
    private val tonKitManager: TonKitManager,
    private val dispatcherProvider: DispatcherProvider
) : GetTonAddressUseCase {

    override suspend fun getAddress(account: Account): String =
        withContext(dispatcherProvider.io) {
            // The mini app identifies the user by the account's default (V4R2) TON address.
            tonKitManager.getReceiveAddress(account, TonWalletVersion.V4R2)
        }
}
