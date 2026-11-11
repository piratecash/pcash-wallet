package cash.p.terminal.core.usecase

import com.m2049r.xmrwallet.service.MoneroWalletService

/** Built on first use: on desktop the root needs an open vault, and the graph can resolve before that. */
class MoneroWalletServiceProvider(private val storage: MoneroWalletStorage) {
    val service: MoneroWalletService by lazy { MoneroWalletService(storage.walletsRoot()) }
}
