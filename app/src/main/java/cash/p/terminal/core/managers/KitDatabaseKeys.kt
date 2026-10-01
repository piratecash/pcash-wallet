package cash.p.terminal.core.managers

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The only caller of [KitDatabaseKeyProvider]: its check-generate-persist is not atomic across kits. */
class KitDatabaseKeys(private val keyProvider: KitDatabaseKeyProvider) {
    private val mutex = Mutex()

    suspend fun awaitKey(accountId: String): ByteArray = mutex.withLock {
        awaitUnlockedKey(accountId)
    }

    suspend fun remove(accountId: String) = mutex.withLock {
        keyProvider.remove(accountId)
    }

    private suspend fun awaitUnlockedKey(accountId: String): ByteArray {
        while (true) {
            try {
                return keyProvider.keyFor(accountId)
            } catch (_: KitDatabaseKeyLockedException) {
                delay(KEYSTORE_RETRY_DELAY_MS)
            }
        }
    }

    private companion object {
        const val KEYSTORE_RETRY_DELAY_MS = 500L
    }
}
