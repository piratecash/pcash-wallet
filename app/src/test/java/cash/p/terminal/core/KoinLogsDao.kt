package cash.p.terminal.core

import io.horizontalsystems.core.logger.AppLog
import io.horizontalsystems.core.storage.LogsDao
import io.mockk.mockk
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module

/**
 * Serves [dao] to [AppLog] through Koin, read on every call so a test can swap it mid-way.
 * [close] releases only what this instance added: the Koin it started, or its module in a running one.
 */
internal class KoinLogsDao(var dao: LogsDao = mockk(relaxed = true)) : AutoCloseable {
    private val logsDaoModule = module { factory<LogsDao> { dao } }
    private val ownsKoin = GlobalContext.getOrNull() == null

    init {
        if (ownsKoin) startKoin { modules(logsDaoModule) } else loadKoinModules(logsDaoModule)
    }

    override fun close() {
        if (ownsKoin) stopKoin() else unloadKoinModules(logsDaoModule)
    }
}
