package cash.p.terminal.ledger.account

import cash.p.terminal.ledger.Speculos
import cash.p.terminal.ledger.account.AccountFixtures.ACCOUNT_ID
import cash.p.terminal.ledger.domain.LedgerAppAccess
import cash.p.terminal.ledger.domain.LedgerAppKeyFetchers
import cash.p.terminal.ledger.domain.LedgerSigningKey
import cash.p.terminal.ledger.domain.LedgerUserOperations
import cash.p.terminal.ledgerkit.LedgerSession
import cash.p.terminal.wallet.IHardwarePublicKeyStorage
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.TokenQuery
import io.horizontalsystems.hdwalletkit.Mnemonic
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk

/** Keys and signing keys read from the speculos app the way the wallet stores and checks them. */
object AccountSpeculos {
    val seed: ByteArray = Mnemonic().toSeed(Speculos.SEED.split(" "))

    suspend fun fetchStoredKey(session: LedgerSession, query: TokenQuery): HardwarePublicKey {
        val app = appFor(query)
        return session.withApp(app.appName, "0") { LedgerAccountKeyFetcher(app).fetchKey(it, query) }
            .toHardwarePublicKey(ACCOUNT_ID)
    }

    /** A signing key that checks the device against the key it reported, as before every real signature. */
    suspend fun signingKey(session: LedgerSession, query: TokenQuery): LedgerSigningKey {
        val stored = fetchStoredKey(session, query)
        val storage = mockk<IHardwarePublicKeyStorage> {
            coEvery { getKey(ACCOUNT_ID, query.blockchainType, query.tokenType) } returns stored
        }
        val fetchers = LedgerAppKeyFetchers(LedgerAccountApp.entries.map(::LedgerAccountKeyFetcher))
        return LedgerSigningKey(
            LedgerAppAccess(session, fetchers, storage, LedgerUserOperations()),
            AccountFixtures.account,
            stored,
        )
    }

    // The app refuses a display it cannot complete, so returning proves the user saw the address.
    suspend fun verifyAddress(session: LedgerSession, query: TokenQuery) {
        val wallet = mockk<Wallet> { every { token.tokenQuery } returns query }
        Speculos.approving {
            session.withApp(appFor(query).appName, "0") { LedgerAccountAddressVerifier().verify(it, wallet, "") }
        }
    }

    private fun appFor(query: TokenQuery) = checkNotNull(LedgerAccountApp.specFor(query)).second
}
