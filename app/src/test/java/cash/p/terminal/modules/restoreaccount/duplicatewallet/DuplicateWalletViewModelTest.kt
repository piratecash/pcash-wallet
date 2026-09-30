package cash.p.terminal.modules.restoreaccount.duplicatewallet

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import cash.p.beam.BeamWalletSession
import cash.p.beam.RestoreSource
import cash.p.terminal.core.IAccountFactory
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.managers.BeamDatabaseKeyProvider
import cash.p.terminal.core.managers.BeamNetwork
import cash.p.terminal.core.managers.BeamSessionFactory
import cash.p.terminal.core.managers.BeamStorageLocator
import cash.p.terminal.core.managers.RestoreSettings
import cash.p.terminal.core.managers.RestoreSettingsManager
import cash.p.terminal.core.managers.RestoreSettingsTestFixture
import cash.p.terminal.core.usecase.MoneroWalletUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IEnabledWalletStorage
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.EnabledWallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.DispatcherProvider
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `copyAccount` runs on `Dispatchers.IO`, so tests await its real completion signal. */
private const val SAVE_TIMEOUT_MS = 5_000L

/** Differs from the source account's passphrase, so it derives a different seed. */
private const val NEW_PASSPHRASE = "new-passphrase"

/** Source-side passphrase, for the cases where the source is not passphrase-less. */
private const val OLD_PASSPHRASE = "source-passphrase"

@OptIn(ExperimentalCoroutinesApi::class)
class DuplicateWalletViewModelTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()
    private val accountManager: IAccountManager = mockk(relaxed = true)
    private val accountFactory: IAccountFactory = mockk(relaxed = true)
    private val moneroWalletUseCase: MoneroWalletUseCase = mockk(relaxed = true)
    private val enabledWalletStorage: IEnabledWalletStorage = mockk(relaxed = true)
    private val walletManager: IWalletManager = mockk(relaxed = true)
    private val restoreSettingsManager: RestoreSettingsManager = mockk(relaxed = true)
    private val localStorage: ILocalStorage = mockk(relaxed = true)
    private val marketKit: MarketKitWrapper = mockk(relaxed = true)
    private val viewModelStore = ViewModelStore()
    private var createdViewModel: DuplicateWalletViewModel? = null

    private val usdtQuery = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xusdt"))
    private val scamQuery = TokenQuery(BlockchainType.BinanceSmartChain, TokenType.Eip20("0xscam"))
    private val pythAddress = "HZ1JovNiVvGrGNiiYvEozEVgZ58xaU3RKwX8eACQBCt3"
    private val unsupportedQuery = TokenQuery(BlockchainType.Solana, TokenType.Spl(pythAddress))

    private val sourceWords = List(11) { "abandon" } + "about"

    private val accountToCopy = Account(
        id = "source-account-id",
        name = "Main",
        type = AccountType.Mnemonic(sourceWords, ""),
        origin = AccountOrigin.Restored,
        level = 0
    )

    private val newAccount = accountToCopy.copy(id = "new-account-id", name = "Main copy")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { accountFactory.getUniqueName(any(), any()) } answers { firstArg() }
        // Mirrors the requested type so tests can tell a same-identity copy from a different-identity one.
        every { accountFactory.account(any(), any(), any(), any(), any()) } answers {
            newAccount.copy(type = secondArg(), origin = thirdArg())
        }
        every { restoreSettingsManager.settings(any(), any()) } returns RestoreSettings()
        // Nothing is known to MarketKit unless a test says otherwise
        every { marketKit.tokens(any<List<TokenQuery>>()) } returns emptyList()
    }

    @After
    fun tearDown() {
        // copyAccount uses real IO; let its parent job resume on test Main before resetting Main.
        runBlocking {
            withTimeout(SAVE_TIMEOUT_MS) {
                createdViewModel?.viewModelScope?.coroutineContext?.get(Job)?.children?.forEach { it.join() }
            }
        }
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    // Matching source/destination addresses used to suppress the review; they no longer do.
    @Test
    fun createAccount_passphraseUnchanged_stillRaisesReviewForUnknownToken() {
        sourceWallets(enabledWallet(usdtQuery), enabledWallet(scamQuery))
        curate(usdtQuery)

        val viewModel = createViewModel()
        viewModel.createAccount()
        awaitUiState(viewModel) { it.tokenReview != null }

        val declined = requireNotNull(viewModel.uiState.tokenReview).wallets.single().tokens
        assertEquals(listOf(scamQuery.id), declined.map { it.tokenQueryId })
        verify(exactly = 0) { accountManager.save(any(), any()) }
    }

    @Test
    fun createAccount_passphraseAdded_copiesOnlyTokensKnownToMarketKit() {
        sourceWallets(enabledWallet(usdtQuery), enabledWallet(scamQuery))
        curate(usdtQuery)
        val saved = captureSavedWallets()

        val viewModel = createViewModel()
        viewModel.apply {
            onTogglePassphrase(true)
            enterPassphrase(NEW_PASSPHRASE)
            createAccount()
        }
        awaitUiState(viewModel) { it.tokenReview != null }
        viewModel.onApproveTokens(emptyMap())

        assertEquals(listOf(usdtQuery.id), awaitSaved(saved).map { it.tokenQueryId })
    }

    @Test
    fun createAccount_passphraseAddedAndNothingCurated_savesAccountWithoutWallets() {
        sourceWallets(enabledWallet(scamQuery))
        val saved = captureSavedWallets()

        val viewModel = createViewModel()
        viewModel.apply {
            onTogglePassphrase(true)
            enterPassphrase(NEW_PASSPHRASE)
            createAccount()
        }
        awaitUiState(viewModel) { it.tokenReview != null }
        viewModel.onApproveTokens(emptyMap())

        assertTrue(awaitSaved(saved).isEmpty())
        verify { accountManager.save(any(), any()) }
    }

    @Test
    fun createAccount_catalogResolvesNothing_raisesReviewInsteadOfCopying() {
        sourceWallets(enabledWallet(usdtQuery))

        val viewModel = createViewModel()
        viewModel.createAccount()
        awaitUiState(viewModel) { it.tokenReview != null }

        val declined = requireNotNull(viewModel.uiState.tokenReview).wallets.single().tokens
        assertEquals(listOf(usdtQuery.id), declined.map { it.tokenQueryId })
        verify(exactly = 0) { accountManager.save(any(), any()) }
    }

    @Test
    fun createAccount_trustedToken_carriesNewAccountIdAndCuratedMetadata() {
        sourceWallets(
            enabledWallet(usdtQuery).copy(
                coinName = "Free Airdrop",
                coinCode = "SCAM",
                coinDecimals = 18,
                coinImage = "scam-url"
            )
        )
        curate(usdtQuery)
        val saved = captureSavedWallets()

        createViewModel().createAccount()

        val copied = awaitSaved(saved).single()
        assertEquals(newAccount.id, copied.accountId)
        assertEquals("Tether", copied.coinName)
        assertEquals("USDT", copied.coinCode)
        assertEquals(6, copied.coinDecimals)
        assertNull(copied.coinImage)
    }

    @Test
    fun createAccount_curatedTokenTypeUnsupported_copiesItWithSourceDecimalsAndCuratedMetadata() {
        sourceWallets(spoofedUnsupportedSourceRow())
        curateUnsupportedPyth()
        val saved = captureSavedWallets()

        createViewModel().createAccount()

        val copied = awaitSaved(saved).single()
        assertEquals(unsupportedQuery.id, copied.tokenQueryId)
        assertEquals("Pyth Network", copied.coinName)
        assertEquals("PYTH", copied.coinCode)
        assertEquals(18, copied.coinDecimals)
    }

    @Test
    fun createAccount_passphraseAdded_copiesCuratedTokenTypeUnsupported() =
        assertUnsupportedRowCopied(sourcePassphrase = "", expectedPassphrase = NEW_PASSPHRASE) {
            onTogglePassphrase(true)
            enterPassphrase(NEW_PASSPHRASE)
        }

    @Test
    fun createAccount_passphraseReplaced_copiesCuratedTokenTypeUnsupported() =
        assertUnsupportedRowCopied(OLD_PASSPHRASE, expectedPassphrase = NEW_PASSPHRASE) {
            enterPassphrase(NEW_PASSPHRASE)
        }

    @Test
    fun createAccount_existingPassphraseDisabled_copiesCuratedTokenTypeUnsupported() =
        assertUnsupportedRowCopied(OLD_PASSPHRASE, expectedPassphrase = "") {
            onTogglePassphrase(false)
        }

    @Test
    fun createAccount_existingPassphraseUnchanged_copiesCuratedTokenTypeUnsupported() =
        assertUnsupportedRowCopied(OLD_PASSPHRASE, expectedPassphrase = OLD_PASSPHRASE) {}

    @Test
    fun createAccount_sameContractInTwoCases_copiesOneCuratedRow() {
        val checksummed = TokenQuery(BlockchainType.Ethereum, TokenType.Eip20("0xUSDT"))
        sourceWallets(enabledWallet(checksummed), enabledWallet(usdtQuery))
        // MarketKit's LIKE '%reference' fallback ignores ASCII case, so both forms resolve to the curated token.
        val curated = Token(
            coin = Coin(uid = "tether", name = "Tether", code = "USDT"),
            blockchain = Blockchain(BlockchainType.Ethereum, "Ethereum", null),
            type = usdtQuery.tokenType,
            decimals = 6
        )
        every { marketKit.tokens(any<List<TokenQuery>>()) } answers {
            firstArg<List<TokenQuery>>()
                .filter { it.id.lowercase() == usdtQuery.id }
                .map { curated }
                .distinct()
        }
        val saved = captureSavedWallets()

        createViewModel().createAccount()

        assertEquals(listOf(usdtQuery.id), awaitSaved(saved).map { it.tokenQueryId })
    }

    @Test
    fun createAccount_unknownToken_doesNotCopyRestoreSettingsOfItsBlockchain() {
        sourceWallets(enabledWallet(usdtQuery), enabledWallet(scamQuery))
        curate(usdtQuery)
        val saved = captureSavedWallets()

        val viewModel = createViewModel()
        viewModel.apply {
            onTogglePassphrase(true)
            enterPassphrase(NEW_PASSPHRASE)
            createAccount()
        }
        awaitUiState(viewModel) { it.tokenReview != null }
        viewModel.onApproveTokens(emptyMap())
        awaitSaved(saved)

        verify { restoreSettingsManager.settings(accountToCopy, BlockchainType.Ethereum) }
        verify {
            restoreSettingsManager.save(
                any(),
                match { it.id == newAccount.id },
                BlockchainType.Ethereum
            )
        }
        verify(exactly = 0) {
            restoreSettingsManager.save(any(), any(), BlockchainType.BinanceSmartChain)
        }
    }

    @Test
    fun createAccount_curatedLookupFails_persistsNothing() {
        sourceWallets(enabledWallet(usdtQuery))
        every { marketKit.tokens(any<List<TokenQuery>>()) } throws IllegalStateException("coin catalog unavailable")

        val viewModel = createViewModel()
        viewModel.createAccount()
        awaitUiState(viewModel) { it.error != null }

        verify(exactly = 0) { accountManager.save(any(), any()) }
        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
        assertEquals("coin catalog unavailable", viewModel.uiState.error)
        assertTrue(viewModel.uiState.createButtonEnabled)
    }

    @Test
    fun createAccount_beamDisabledCreatedSource_restoresWhenEnabledAfterRestart() {
        sourceWallets()
        val fixture = RestoreSettingsTestFixture()
        val saved = captureSavedWallets()
        val created = slot<Account>()
        var intentAtPublication = false
        every { accountManager.save(capture(created), any()) } answers {
            intentAtPublication = fixture.manager().hasBeamRestoreIntent(created.captured)
        }

        createViewModel(OLD_PASSPHRASE, AccountOrigin.Created, fixture.manager()).createAccount()

        assertTrue(awaitSaved(saved).isEmpty())
        assertTrue(intentAtPublication)
        val duplicate = created.captured
        assertEquals(AccountOrigin.Created, duplicate.origin)
        assertNotEquals(accountToCopy.id, duplicate.id)
        val mnemonic = duplicate.type as AccountType.Mnemonic
        assertEquals(sourceWords, mnemonic.words)
        assertEquals(OLD_PASSPHRASE, mnemonic.passphrase)
        assertLateBeamEnableRestores(duplicate, fixture.manager())
    }

    @Test
    fun createAccount_passphraseChangedCreatedSource_restoresWhenBeamEnabledLater() {
        sourceWallets()
        val fixture = RestoreSettingsTestFixture()
        val saved = captureSavedWallets()

        createViewModel(OLD_PASSPHRASE, AccountOrigin.Created, fixture.manager()).apply {
            enterPassphrase(NEW_PASSPHRASE)
            createAccount()
        }
        awaitSaved(saved)

        val created = slot<Account>()
        verify { accountManager.save(capture(created), any()) }
        assertEquals(NEW_PASSPHRASE, (created.captured.type as AccountType.Mnemonic).passphrase)
        assertEquals(AccountOrigin.Created, created.captured.origin)
        assertLateBeamEnableRestores(created.captured, fixture.manager())
    }

    @Test
    fun createAccount_beamEnabled_savesIntentAfterCopiedSettingsBeforePublication() {
        val beam = TokenQuery(BlockchainType.Beam, TokenType.Native)
        sourceWallets(enabledWallet(beam))
        curate(beam)
        val saved = captureSavedWallets()

        createViewModel(sourceOrigin = AccountOrigin.Created).createAccount()
        awaitSaved(saved)

        verifyOrder {
            restoreSettingsManager.save(any(), match { it.id == newAccount.id }, BlockchainType.Beam)
            restoreSettingsManager.saveBeamRestoreIntent(match { it.id == newAccount.id })
            accountManager.save(match { it.id == newAccount.id && it.origin == AccountOrigin.Created }, any())
        }
    }

    @Test
    fun createAccount_beamIntentStorageFails_doesNotPublishAccountOrWallets() {
        sourceWallets()
        val fixture = RestoreSettingsTestFixture()
        every { fixture.storage.save(any()) } throws IllegalStateException("Storage unavailable")
        val viewModel = createViewModel(sourceOrigin = AccountOrigin.Created, settingsManager = fixture.manager())

        viewModel.createAccount()
        awaitUiState(viewModel) { it.error != null }

        verify(exactly = 0) { accountManager.save(any(), any()) }
        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
        assertFalse(viewModel.uiState.closeScreen)
        assertTrue(viewModel.uiState.createButtonEnabled)
        assertFalse(fixture.manager().hasBeamRestoreIntent(newAccount))
    }

    // A post-commit failure test (saveEnabledWallets throwing after accountManager.save succeeds)
    // is intentionally omitted: copyAccount leaves that segment uncaught, and kotlinx-coroutines-test
    // reports the resulting uncaught exception via a JVM-wide handler, so it surfaces as a spurious
    // failure in an unrelated later test instead of this one (confirmed empirically).

    @Test
    fun createAccount_passphraseChangedWithManuallyAddedToken_exposesReviewAndWritesNothing() {
        sourceWallets(enabledWallet(scamQuery))
        val viewModel = createViewModel()

        viewModel.apply {
            onTogglePassphrase(true)
            enterPassphrase(NEW_PASSPHRASE)
            createAccount()
        }
        awaitUiState(viewModel) { it.tokenReview != null }

        assertFalse(viewModel.uiState.closeScreen)
        verify(exactly = 0) { accountManager.save(any(), any()) }
        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
        verify(exactly = 0) { restoreSettingsManager.save(any(), any(), any()) }
    }

    @Test
    fun onDismissTokenReview_reviewExposed_writesNothingAndReenablesCreateButton() {
        sourceWallets(enabledWallet(scamQuery))
        val viewModel = createViewModel()
        viewModel.apply {
            onTogglePassphrase(true)
            enterPassphrase(NEW_PASSPHRASE)
            createAccount()
        }
        awaitUiState(viewModel) { it.tokenReview != null }

        viewModel.onDismissTokenReview()

        assertNull(viewModel.uiState.tokenReview)
        assertTrue(viewModel.uiState.createButtonEnabled)
        verify(exactly = 0) { accountManager.save(any(), any()) }
        coVerify(exactly = 0) { walletManager.saveEnabledWallets(any()) }
    }

    @Test
    fun onApproveTokens_approveAll_savesEveryDeclinedRowAndCloses() {
        sourceWallets(enabledWallet(usdtQuery), enabledWallet(scamQuery))
        curate(usdtQuery)
        val saved = captureSavedWallets()

        val viewModel = createViewModel()
        viewModel.createAccount()
        awaitUiState(viewModel) { it.tokenReview != null }
        val review = requireNotNull(viewModel.uiState.tokenReview)

        viewModel.onApproveTokens(review.allTokenIds)

        val savedWallets = awaitSaved(saved)
        assertEquals(setOf(usdtQuery.id, scamQuery.id), savedWallets.map { it.tokenQueryId }.toSet())
        verify(exactly = 1) { accountManager.save(any(), any()) }
        awaitUiState(viewModel) { it.closeScreen }
        assertTrue(viewModel.uiState.closeScreen)
    }

    // Skip-all is a non-null empty approval, not an abort: the `approved == null` guard in
    // `copyAccount` is bypassed, so the account is written with every catalog-resolved row.
    @Test
    fun onApproveTokens_skipAll_writesAccountWithCatalogRowsButWithoutDeclinedRow() {
        sourceWallets(enabledWallet(usdtQuery), enabledWallet(scamQuery))
        curate(usdtQuery)
        val saved = captureSavedWallets()

        val viewModel = createViewModel()
        viewModel.apply {
            onTogglePassphrase(true)
            enterPassphrase(NEW_PASSPHRASE)
            createAccount()
        }
        awaitUiState(viewModel) { it.tokenReview != null }

        viewModel.onApproveTokens(emptyMap())

        assertEquals(listOf(usdtQuery.id), awaitSaved(saved).map { it.tokenQueryId })
        verify(exactly = 1) { accountManager.save(any(), any()) }
    }

    // Also checks the created account's passphrase, so a false pass can't hide a silently-failed passphrase apply.
    private fun assertUnsupportedRowCopied(
        sourcePassphrase: String,
        expectedPassphrase: String,
        enterDestinationPassphrase: DuplicateWalletViewModel.() -> Unit
    ) {
        sourceWallets(spoofedUnsupportedSourceRow())
        curateUnsupportedPyth()
        val saved = captureSavedWallets()

        createViewModel(sourcePassphrase).apply {
            enterDestinationPassphrase()
            createAccount()
        }

        assertEquals(18, awaitSaved(saved).single().coinDecimals)
        val created = slot<Account>()
        verify { accountManager.save(capture(created), any()) }
        assertEquals(expectedPassphrase, (created.captured.type as AccountType.Mnemonic).passphrase)
        verifyOrder {
            restoreSettingsManager.saveBeamRestoreIntent(created.captured)
            accountManager.save(created.captured, any())
        }
    }

    private fun assertLateBeamEnableRestores(account: Account, settingsManager: RestoreSettingsManager) = runBlocking {
        val directory = temporaryFolder.newFolder()
        val locator = mockk<BeamStorageLocator> {
            every { storagePath(account.id, BeamNetwork.Mainnet) } returns directory
            every { databaseFile(account.id, BeamNetwork.Mainnet) } returns directory.resolve("wallet.db")
        }
        val keys = mockk<BeamDatabaseKeyProvider> {
            every { ensureAvailable(account.id) } returns Unit
            every { keyForInitialization(account.id, BeamNetwork.Mainnet) } returns
                BeamDatabaseKeyProvider.Key(ByteArray(32) { 3 }, isNew = true)
        }
        val dispatchers = mockk<DispatcherProvider> { every { io } returns Dispatchers.Unconfined }
        val sdk = mockk<BeamSessionFactory.Factory>()
        coEvery { sdk.restore(any(), any(), any(), any()) } returns mockk<BeamWalletSession>()

        BeamSessionFactory(keys, locator, dispatchers, settingsManager, sdk).open(account, BeamNetwork.Mainnet)

        coVerify(exactly = 1) { sdk.restore(any(), any(), any(), RestoreSource.SnapshotThenScan()) }
        coVerify(exactly = 0) { sdk.createNew(any(), any(), any()) }
        coVerify(exactly = 0) { sdk.openExisting(any(), any()) }
    }

    private fun DuplicateWalletViewModel.enterPassphrase(value: String) {
        onChangePassphrase(value)
        onChangePassphraseConfirmation(value)
    }

    /** [sourcePassphrase] varies the source identity; the destination one is set through the UI. */
    private fun createViewModel(
        sourcePassphrase: String = "",
        sourceOrigin: AccountOrigin = accountToCopy.origin,
        settingsManager: RestoreSettingsManager = restoreSettingsManager,
    ) = DuplicateWalletViewModel(
        accountToCopy = accountToCopy.copy(
            type = AccountType.Mnemonic(sourceWords, sourcePassphrase),
            origin = sourceOrigin,
        ),
        accountManager = accountManager,
        accountFactory = accountFactory,
        moneroWalletUseCase = moneroWalletUseCase,
        enabledWalletStorage = enabledWalletStorage,
        walletManager = walletManager,
        restoreSettingsManager = settingsManager,
        localStorage = localStorage,
        marketKit = marketKit
    ).also {
        createdViewModel = it
        viewModelStore.put("duplicate-wallet", it)
    }

    private fun enabledWallet(query: TokenQuery) = EnabledWallet(
        tokenQueryId = query.id,
        accountId = accountToCopy.id,
        coinName = "Tether",
        coinCode = "USDT",
        coinDecimals = 6,
        coinImage = "image-url"
    )

    private fun sourceWallets(vararg wallets: EnabledWallet) {
        every { enabledWalletStorage.enabledWallets(accountToCopy.id) } returns wallets.toList()
    }

    /** Source row whose metadata is attacker-shaped: only its decimals may ever be carried over. */
    private fun spoofedUnsupportedSourceRow() = enabledWallet(unsupportedQuery).copy(
        coinName = "Spoofed",
        coinCode = "SPF",
        coinDecimals = 18
    )

    /** `CoinDao` maps a catalog row with null decimals to [TokenType.Unsupported] plus a zero. */
    private fun curateUnsupportedPyth() {
        val curated = Token(
            coin = Coin(uid = "pyth-network", name = "Pyth Network", code = "PYTH"),
            blockchain = Blockchain(BlockchainType.Solana, "Solana", null),
            type = TokenType.Unsupported("spl", pythAddress),
            decimals = 0
        )
        every { marketKit.tokens(any<List<TokenQuery>>()) } answers {
            firstArg<List<TokenQuery>>().filter { it.id == unsupportedQuery.id }.map { curated }
        }
    }

    /** Makes MarketKit resolve only [queries], mirroring the curated coin database. */
    private fun curate(vararg queries: TokenQuery) {
        val curated = queries.associateWith { query ->
            Token(
                coin = Coin(uid = "tether", name = "Tether", code = "USDT"),
                blockchain = Blockchain(query.blockchainType, "Blockchain", null),
                type = query.tokenType,
                decimals = 6
            )
        }
        every { marketKit.tokens(any<List<TokenQuery>>()) } answers {
            firstArg<List<TokenQuery>>().mapNotNull { curated[it] }
        }
    }

    private fun captureSavedWallets(): CompletableDeferred<List<EnabledWallet>> {
        val saved = CompletableDeferred<List<EnabledWallet>>()
        coEvery { walletManager.saveEnabledWallets(any()) } answers { saved.complete(firstArg()) }
        return saved
    }

    private fun awaitSaved(saved: CompletableDeferred<List<EnabledWallet>>): List<EnabledWallet> =
        runBlocking { withTimeout(SAVE_TIMEOUT_MS) { saved.await() } }

    /** `uiState` is plain Compose state set from a background dispatcher, so tests poll for it. */
    private fun awaitUiState(
        viewModel: DuplicateWalletViewModel,
        until: (DuplicateWalletUiState) -> Boolean
    ) {
        runBlocking {
            withTimeout(SAVE_TIMEOUT_MS) {
                while (!until(viewModel.uiState)) {
                    delay(10)
                }
            }
        }
    }
}
