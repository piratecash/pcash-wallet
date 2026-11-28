package cash.p.terminal.ledger.domain

import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.entities.TokenQuery

data class LedgerAppProgress(val index: Int, val total: Int, val appName: String)

class LedgerSkippedApp(val appName: String, val reason: Exception)

class LedgerKeyRead(val keys: List<LedgerKey>, val skipped: List<LedgerSkippedApp>) {
    val skippedApps: List<String> get() = skipped.map { it.appName }
}

/**
 * Opens each app the queries need, one by one; a missing, outdated or declined app is skipped, not fatal.
 * Keys from more than one app are confirmed to come from a single device.
 */
class LedgerKeyReader(
    private val session: LedgerSession,
    private val fetchers: LedgerAppKeyFetchers,
    private val userOperations: LedgerUserOperations,
) {
    /** With [expectedConnection], throws [LedgerConnectionChangedException] once the session's connection differs. */
    suspend fun read(
        queries: List<TokenQuery>,
        expectedConnection: Long?,
        onOpeningApp: (LedgerAppProgress) -> Unit,
    ): LedgerKeyRead = userOperations.run {
        val groups = fetchers.groupByApp(queries)
        // A multi-app read ends with reopening the first app to confirm the device did not change.
        val steps = if (groups.size > 1) groups.size + 1 else groups.size
        val keys = mutableListOf<LedgerKey>()
        val skipped = mutableListOf<LedgerSkippedApp>()
        groups.forEachIndexed { index, (fetcher, appQueries) ->
            val progress = LedgerAppProgress(index + 1, steps, fetcher.appName)
            try {
                keys += inApp(fetcher, progress, expectedConnection, onOpeningApp) { fetcher.fetchKeys(it, appQueries) }
            } catch (error: LedgerException) {
                if (!error.isSkippable) throw error
                skipped += LedgerSkippedApp(fetcher.appName, error.forApp(fetcher.appName))
            }
        }
        ensureSameDevice(keys, steps, expectedConnection, onOpeningApp)
        LedgerKeyRead(keys, skipped)
    }

    private suspend fun <T> inApp(
        fetcher: LedgerAppKeyFetcher,
        progress: LedgerAppProgress,
        expectedConnection: Long?,
        onOpeningApp: (LedgerAppProgress) -> Unit,
        block: suspend (LedgerTransport) -> T,
    ): T {
        onOpeningApp(progress)
        return session.withApp(fetcher.appName, LedgerAppRequirements.minVersion(fetcher.appName)) { transport ->
            if (expectedConnection != null && session.connectionId != expectedConnection) {
                throw LedgerConnectionChangedException()
            }
            block(transport)
        }
    }

    // Every app switch reconnects, so another Ledger may have answered the later apps: re-read the first key.
    private suspend fun ensureSameDevice(
        keys: List<LedgerKey>,
        steps: Int,
        expectedConnection: Long?,
        onOpeningApp: (LedgerAppProgress) -> Unit,
    ) {
        val first = keys.firstOrNull() ?: return
        val fetcher = checkNotNull(fetchers.fetcherFor(first.spec.query))
        if (keys.all { fetchers.fetcherFor(it.spec.query) == fetcher }) return
        val sameDevice = try {
            inApp(fetcher, LedgerAppProgress(steps, steps, fetcher.appName), expectedConnection, onOpeningApp) {
                fetcher.holdsKey(it, first.spec.query, first.key, first.publicKey)
            }
        } catch (error: LedgerException) {
            throw error.forApp(fetcher.appName)
        }
        if (!sameDevice) throw LedgerIdentityMismatchException()
    }

    private val LedgerException.isSkippable: Boolean
        get() = this is LedgerException.AppNotInstalled ||
            this is LedgerException.UserRejected ||
            this is LedgerException.AppVersionTooOld
}
