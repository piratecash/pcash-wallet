package cash.p.terminal.ledger.domain

import cash.p.terminal.ledgerkit.DerivationPath
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.hardware.HardwareKeyCoin
import cash.p.terminal.wallet.hardware.HardwareKeySpec
import cash.p.terminal.wallet.hardware.HardwarePublicKeySpecs
import cash.p.terminal.wallet.hardware.HardwareVendor

/** Reads public keys through one Ledger device app. */
interface LedgerAppKeyFetcher {
    val appName: String

    fun supports(query: TokenQuery): Boolean

    /** Runs inside the open [appName]; [transport] belongs to this call alone. */
    suspend fun fetchKey(transport: LedgerTransport, query: TokenQuery): LedgerKey

    /** [fetchKey] for every query of one app session; an app whose key read asks the user reads it once. */
    suspend fun fetchKeys(transport: LedgerTransport, queries: List<TokenQuery>): List<LedgerKey> =
        queries.map { fetchKey(transport, it) }

    /** Whether the open app holds the stored [key]; must never ask the user, it runs before every signature. */
    suspend fun holdsKey(transport: LedgerTransport, query: TokenQuery, key: String, publicKey: ByteArray): Boolean =
        fetchKey(transport, query).key == key
}

/** A device app that serves the keys of one [coin]. */
interface LedgerCoinApp {
    val appName: String
    val coin: HardwareKeyCoin
}

/** The Ledger spec of [query] with the app among these that serves its coin, or null when none does. */
internal fun <T : LedgerCoinApp> List<T>.specFor(query: TokenQuery): Pair<HardwareKeySpec, T>? {
    val spec = HardwarePublicKeySpecs.specFor(query, HardwareVendor.Ledger) ?: return null
    return firstOrNull { it.coin == spec.coin }?.let { spec to it }
}

internal fun HardwareKeySpec.ledgerPath(): DerivationPath = DerivationPath.parse(derivationPath)

class LedgerKey(
    val spec: HardwareKeySpec,
    val key: String,
    val publicKey: ByteArray,
) {
    fun toHardwarePublicKey(accountId: String): HardwarePublicKey =
        HardwarePublicKeySpecs.toHardwarePublicKey(spec, key, publicKey, accountId)
}

class LedgerAppKeyFetchers(private val fetchers: List<LedgerAppKeyFetcher>) {

    fun supports(query: TokenQuery): Boolean = fetcherFor(query) != null

    fun fetcherFor(query: TokenQuery): LedgerAppKeyFetcher? = fetchers.firstOrNull { it.supports(query) }

    /** Each app is opened once, in the order its first query appears; unsupported queries are dropped. */
    fun groupByApp(queries: List<TokenQuery>): List<Pair<LedgerAppKeyFetcher, List<TokenQuery>>> =
        queries.distinct()
            .mapNotNull { query -> fetcherFor(query)?.let { it to query } }
            .groupBy({ it.first }, { it.second })
            .toList()
}
