package cash.p.terminal.modules.walletconnect.storage

import cash.p.terminal.core.storage.StorageRouter
import cash.p.terminal.core.storage.acrossAccounts
import cash.p.terminal.core.storage.forEachAccount
import cash.p.terminal.core.storage.writePerStore

class WCSessionStorage(private val router: StorageRouter) {

    fun getAllSessions(): List<WalletConnectV2Session> {
        return router.acrossAccounts { wcSessionDao().getAll() }
    }

    fun getSessionsByAccountId(accountId: String): List<WalletConnectV2Session> {
        return router.account(accountId)?.wcSessionDao()?.getByAccountId(accountId).orEmpty()
    }

    fun save(sessions: List<WalletConnectV2Session>) {
        router.writePerStore(sessions, { it.accountId }) { wcSessionDao().insert(it) }
    }

    fun deleteSessionsByTopics(topics: List<String> = listOf()) {
        router.forEachAccount { wcSessionDao().deleteByTopics(topics) }
    }

}
