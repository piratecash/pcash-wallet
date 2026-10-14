package cash.p.terminal.wallet.useCases

import cash.p.terminal.wallet.Account

interface IRemoveMoneroWalletFilesUseCase {
    suspend operator fun invoke(account: Account): Boolean
    suspend operator fun invoke(walletInnerName: String): Boolean
}
