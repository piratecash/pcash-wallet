package cash.p.terminal.core.usecase

import cash.p.terminal.core.storage.AppDatabaseProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.useCases.IGetMoneroWalletFilesNameUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetMoneroWalletFilesNameUseCase(
    private val databases: AppDatabaseProvider
) : IGetMoneroWalletFilesNameUseCase {
    override suspend fun invoke(account: Account) = withContext(Dispatchers.IO) {
        when (account.type) {
            is AccountType.MnemonicMonero -> {
                (account.type as AccountType.MnemonicMonero).walletInnerName
            }

            is AccountType.Mnemonic,
            is AccountType.TrezorDevice -> {
                databases.database.moneroFileDao().getAssociatedRecord(account.id)?.fileName?.value
            }

            else -> {
                null
            }
        }
    }
}
