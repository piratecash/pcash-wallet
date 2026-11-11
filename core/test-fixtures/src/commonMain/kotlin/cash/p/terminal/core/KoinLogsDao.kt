package cash.p.terminal.core

import io.horizontalsystems.core.logger.AppLog
import io.horizontalsystems.core.logger.LogScope
import io.horizontalsystems.core.storage.LogsDao
import io.mockk.mockk
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module

class FakeLogScope(
    override var userLevel: Int = 0,
    /** Null means every account is visible. */
    var visibleAccountIds: Set<String>? = null,
) : LogScope {
    override fun stampFor(accountId: String?): Int? =
        userLevel.takeIf { accountId == null || visibleAccountIds?.contains(accountId) ?: true }
}

/**
 * Serves [dao] and [scope] to [AppLog] through Koin, read on every call so a test can swap them mid-way.
 * [close] releases only what this instance added: the Koin it started, or its module in a running one.
 */
class KoinLogsDao(
    var dao: LogsDao = mockk(relaxed = true),
    val scope: FakeLogScope = FakeLogScope(),
) : AutoCloseable {
    private val logsDaoModule = module {
        factory<LogsDao> { dao }
        factory<LogScope> { scope }
    }
    private val ownsKoin = GlobalContext.getOrNull() == null

    init {
        if (ownsKoin) startKoin { modules(logsDaoModule) } else loadKoinModules(logsDaoModule)
    }

    override fun close() {
        if (ownsKoin) stopKoin() else unloadKoinModules(logsDaoModule)
    }
}
