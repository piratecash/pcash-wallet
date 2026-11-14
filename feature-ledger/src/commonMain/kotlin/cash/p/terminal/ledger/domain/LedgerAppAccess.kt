package cash.p.terminal.ledger.domain

import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.ledgerkit.apdu.LedgerException
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.useCases.GetHardwarePublicKeyForWalletUseCase
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** The device was proven to be the account's one; [app] is null when a proof of this connection was reused. */
class LedgerIdentityProof(val app: String?, val connectionId: Long)

/** Every device operation of a Ledger account goes through here, so it never runs on another wallet. */
class LedgerAppAccess(
    private val session: LedgerSession,
    private val fetchers: LedgerAppKeyFetchers,
    private val hardwarePublicKeyStorage: IHardwarePublicKeyStorage,
    private val userOperations: LedgerUserOperations,
) {
    private val storedKeyFor = GetHardwarePublicKeyForWalletUseCase(hardwarePublicKeyStorage)

    // Account id -> session connection on which the device last held one of the account's stored keys.
    private val proofs = MutableStateFlow<Map<String, Long>>(emptyMap())

    /**
     * Whether [appName] is open on a held connection, asked without a prompt, so a check never switches apps.
     * False as well when [admit], called once the device is held, throws.
     */
    suspend fun isOpen(appName: String, admit: () -> Unit): Boolean = openAppName(admit) == appName

    fun appNameFor(query: TokenQuery): String? = fetchers.fetcherFor(query)?.appName

    private suspend fun openAppName(admit: () -> Unit): String? = try {
        session.openAppName(admit)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    suspend fun <T> withLedgerApp(
        account: Account,
        query: TokenQuery,
        block: suspend (LedgerTransport) -> T,
    ): T = userOperations.run { withLedgerApp(account, query, admit = {}, switchApp = true, block) }

    /**
     * [withLedgerApp] that runs only if [admit], called once the device is held, does not throw.
     * Without [switchApp] it throws [LedgerException.WrongAppOpen] unless the app is already open.
     */
    suspend fun <T> withLedgerApp(
        account: Account,
        query: TokenQuery,
        admit: () -> Unit,
        switchApp: Boolean,
        block: suspend (LedgerTransport) -> T,
    ): T {
        val storedKey = checkNotNull(storedKeyFor(account, query)) { "No Ledger key stored for ${query.id}" }
        // A token may live on its chain's key; the device is asked for the key that was stored.
        val keyQuery = storedKey.tokenQuery
        val fetcher = checkNotNull(fetchers.fetcherFor(keyQuery)) { "Ledger cannot serve ${keyQuery.id}" }
        val minVersion = LedgerAppRequirements.minVersion(fetcher.appName)
        val checkedBlock: suspend (LedgerTransport) -> T = { transport ->
            if (!fetcher.holdsKey(transport, keyQuery, storedKey.key.value, storedKey.publicKey)) {
                forgetProof(account.id)
                throw LedgerIdentityMismatchException()
            }
            proofs.update { it + (account.id to session.connectionId) }
            block(transport)
        }
        return try {
            if (switchApp) {
                session.withApp(fetcher.appName, minVersion, admit, checkedBlock)
            } else {
                session.withOpenApp(fetcher.appName, minVersion, admit, checkedBlock)
            }
        } catch (error: LedgerException) {
            throw error.forApp(fetcher.appName)
        }
    }

    /** [withLedgerApp] for signing: a refused or garbled answer is a failed signature. */
    suspend fun <T> signWithLedgerApp(
        account: Account,
        query: TokenQuery,
        block: suspend (LedgerTransport) -> T,
    ): T = try {
        withLedgerApp(account, query, block)
    } catch (error: LedgerException.MalformedResponse) {
        throw LedgerSigningFailedException(error.message.orEmpty())
    } catch (error: LedgerException.InvalidRequest) {
        throw LedgerSigningFailedException(error.message.orEmpty())
    }

    fun forgetProof(accountId: String) {
        proofs.update { it - accountId }
    }

    /**
     * Checks the connected device against the account's stored keys, unless this connection already passed
     * such a check. Any installed app holding its stored key proves the device; a removed or outdated app is
     * skipped. Apps that need no extra prompt go first: the one already open, then those the [requested]
     * queries will open anyway.
     */
    suspend fun verifyIdentity(
        account: Account,
        requested: List<TokenQuery>,
    ): LedgerIdentityProof = userOperations.run {
        val connectionId = session.connectionId
        if (proofs.value[account.id] == connectionId) return@run LedgerIdentityProof(app = null, connectionId)
        val walletPublicKey = requireNotNull(account.type as? AccountType.LedgerDevice).walletPublicKey
        val storedKeys = hardwarePublicKeyStorage.getAllPublicKeys(account.id)
        val identityKey = storedKeys.firstOrNull { it.key.value == walletPublicKey }
            ?: throw LedgerIdentityMismatchException()
        val candidates = (listOf(identityKey) + storedKeys)
            .mapNotNull { key -> appNameFor(key.tokenQuery)?.let { it to key.tokenQuery } }
            .distinctBy { (appName, _) -> appName }
            .preferringApps(openAppName(admit = {}), requested.mapNotNull(::appNameFor).toSet())
        var tooOld: LedgerException.AppVersionTooOld? = null
        for ((appName, query) in candidates) {
            try {
                val provenConnection = withLedgerApp(account, query) { session.connectionId }
                return@run LedgerIdentityProof(appName, provenConnection)
            } catch (_: LedgerAppMissingException) {
            } catch (error: LedgerException.AppVersionTooOld) {
                tooOld = tooOld ?: error
            }
        }
        throw tooOld ?: LedgerIdentityAppMissingException(candidates.map { (appName, _) -> appName })
    }

    private fun List<Pair<String, TokenQuery>>.preferringApps(openApp: String?, requestedApps: Set<String>) =
        sortedBy { (appName, _) ->
            when (appName) {
                openApp -> 0
                in requestedApps -> 1
                else -> 2
            }
        }
}

/** The query a stored key was read for. */
internal val HardwarePublicKey.tokenQuery: TokenQuery
    get() = TokenQuery(BlockchainType.fromUid(blockchainType), tokenType)
