package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.StorageRouter
import cash.p.terminal.wallet.IDeletedWalletChecker

class DeletedWalletChecker(private val router: StorageRouter) : IDeletedWalletChecker {
    override suspend fun getDeletedTokenQueryIds(accountId: String): Set<String> =
        router.account(accountId)?.userDeletedWalletDao()?.getDeletedTokenQueryIds(accountId).orEmpty().toSet()
}
