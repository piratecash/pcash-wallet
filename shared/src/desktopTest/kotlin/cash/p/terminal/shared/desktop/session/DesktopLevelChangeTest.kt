package cash.p.terminal.shared.desktop.session

import androidx.room.useReaderConnection
import cash.p.terminal.core.factories.AdapterFactory
import cash.p.terminal.core.managers.AmlStatusManager
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.storage.StorageRouter
import cash.p.terminal.network.binance.api.BinanceApi
import cash.p.terminal.network.pirate.domain.enity.InvestmentData
import cash.p.terminal.network.pirate.domain.enity.TrialPremiumResult
import cash.p.terminal.shared.desktop.session.DesktopKitFilesScanTest.Companion.KIT_TOKENS
import cash.p.terminal.shared.desktop.session.DesktopKitFilesScanTest.Companion.native
import cash.p.terminal.shared.desktop.vault.CreateOutcome
import cash.p.terminal.shared.desktop.vault.DURESS_PASSWORD
import cash.p.terminal.shared.desktop.vault.HIDDEN_PASSWORD
import cash.p.terminal.modules.pin.LevelOutcome
import cash.p.terminal.shared.desktop.vault.MAIN_PASSWORD
import cash.p.terminal.shared.desktop.vault.NESTED_PASSWORD
import cash.p.terminal.shared.desktop.vault.StorageCheckpoint
import cash.p.terminal.shared.desktop.vault.UnlockOutcome
import cash.p.terminal.shared.desktop.vault.awaitLevelRelease
import cash.p.terminal.shared.desktop.vault.password
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.ActiveAccountState
import cash.p.terminal.wallet.IAdapter
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.WalletFactory
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import cash.p.terminal.wallet.tokenQueryId
import cash.p.terminal.wallet.managers.UserManager
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.instance.ResolutionContext
import org.koin.core.instance.SingleInstanceFactory
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The real graph, kits and adapters, offline: a password entered under the lock changes the running wallet. */
class DesktopLevelChangeTest {
    private val graph = DesktopWalletGraph(createTempDirectory("desktop-level-change").toFile()).start()
    private val adapterManager get() = graph.koin.get<IAdapterManager>()
    private val userManager get() = graph.koin.get<UserManager>()
    private val session get() = checkNotNull(graph.vault.openSession())

    @AfterTest
    fun tearDown() = runBlocking<Unit> {
        // The native session is process-wide: a next test could not open its own.
        graph.koin.get<BeamSessionOwner>().close()
        graph.stop()
        graph.dataDirectory.deleteRecursively()
    }

    @Test
    fun unlock_duressActiveAccountBelongsToIt_keepsItsAdapterAndReleasesTheLevelAbove() = runBlocking<Unit> {
        val (main, duress) = mainWithDuress()
        graph.accountManager.setActiveAccountId(duress.id)
        val kept = wallet(native(BlockchainType.Dash), duress)
        graph.activeWallets.value = listOf(kept)
        val keptAdapter = awaitAdapter(kept)

        enterUnderLock(DURESS_PASSWORD)

        assertSame(keptAdapter, adapterManager.getAdapterForWallet<IAdapter>(kept))
        graph.vault.awaitLevelRelease()
        assertNull(session.account(main.id))
        assertEquals(1, userManager.getUserLevel())
        assertEquals(duress.id, graph.accountManager.activeAccount?.id)
        assertEquals(listOf(duress.id), graph.accountManager.accounts.map(Account::id))
    }

    @Test
    fun unlock_duressActiveAccountIsTheMainOne_stopsItsAdapterAndStartsTheDuressOne() = runBlocking<Unit> {
        val (main, duress) = mainWithDuress()
        graph.accountManager.setActiveAccountId(main.id)
        val upper = wallet(native(BlockchainType.Dogecoin), main)
        graph.activeWallets.value = listOf(upper)
        assertNotNull(awaitAdapter(upper))

        enterUnderLock(DURESS_PASSWORD)
        graph.vault.awaitLevelRelease()

        assertEquals(duress.id, graph.accountManager.activeAccount?.id)
        assertNull(adapterManager.getAdapterForWallet<IAdapter>(upper))
        val own = wallet(native(BlockchainType.Dash), duress)
        graph.activeWallets.value = listOf(own)
        assertNotNull(awaitAdapter(own))
    }

    @Test
    fun unlock_duressLastAccountDeletedFromMain_noAccountStaysActive() = runBlocking<Unit> {
        val (main, duress) = mainWithDuress()
        enterUnderLock(DURESS_PASSWORD)
        val selected = saved()
        assertEquals(selected.id, graph.accountManager.activeAccount?.id)
        enterUnderLock(MAIN_PASSWORD)
        awaitAccounts(main, duress, selected)
        graph.accountManager.setActiveAccountId(main.id)
        graph.accountManager.delete(duress.id)
        graph.accountManager.delete(selected.id)

        enterUnderLock(DURESS_PASSWORD)

        assertEquals(1, userManager.getUserLevel())
        assertNull(graph.accountManager.activeAccount)
        assertEquals(emptyList(), graph.accountManager.accounts)
        val state = graph.accountManager.activeAccountStateFlow.first()
        assertNull(assertIs<ActiveAccountState.ActiveAccount>(state).account)
    }

    @Test
    fun unlock_duressBalanceResponseArrivesAfterTheRootMoved_nothingOfTheLevelAboveIsWritten() = runBlocking<Unit> {
        val response = CompletableDeferred<Unit>()
        val parked = holdBalanceResponse(response)
        assertEquals(CreateOutcome.SUCCESS, graph.vault.create(password(MAIN_PASSWORD)))
        assertEquals(LevelOutcome.SUCCESS, graph.vault.setDuressPassword(password(DURESS_PASSWORD)))
        val main = saved()
        withTimeout(WAIT_MS) { while (parked.get() == 0) delay(POLL_MS) }
        graph.checkpoints.onReached = { checkpoint ->
            if (checkpoint == StorageCheckpoint.NARROWING_CHAIN_SPLIT) {
                response.complete(Unit)
                // The stop that follows the admission must not be what saves this write.
                Thread.sleep(SETTLE_MS)
            }
        }

        enterUnderLock(DURESS_PASSWORD)
        graph.vault.awaitLevelRelease()

        assertEquals(0, rowsOfRootPremiumAbout(main.id))
    }

    @Test
    fun unlock_duressBalanceResponseNeverArrives_doesNotWaitForIt() = runBlocking<Unit> {
        val parked = holdBalanceResponse(CompletableDeferred())
        mainWithDuress()
        withTimeout(WAIT_MS) { while (parked.get() == 0) delay(POLL_MS) }

        withTimeout(WAIT_MS) { enterUnderLock(DURESS_PASSWORD) }

        assertEquals(1, userManager.getUserLevel())
    }

    @Test
    fun unlock_nestedDuressFromDuress_followsTheSameWay() = runBlocking<Unit> {
        val (_, duress) = mainWithDuress()
        enterUnderLock(DURESS_PASSWORD)
        assertEquals(LevelOutcome.SUCCESS, graph.vault.setDuressPassword(password(NESTED_PASSWORD)))
        val nested = saved()
        graph.koin.get<StorageRouter>().setLevel(listOf(nested.id), 2)
        val kept = wallet(native(BlockchainType.Dash), nested)
        val hidden = wallet(native(BlockchainType.Dogecoin), duress)
        graph.activeWallets.value = listOf(kept, hidden)
        val keptAdapter = awaitAdapter(kept)

        enterUnderLock(NESTED_PASSWORD)

        assertSame(keptAdapter, adapterManager.getAdapterForWallet<IAdapter>(kept))
        assertNull(adapterManager.getAdapterForWallet<IAdapter>(hidden))
        assertEquals(2, userManager.getUserLevel())
        assertEquals(listOf(nested.id), graph.accountManager.accounts.map(Account::id))
    }

    @Test
    fun unlock_mainOverNarrowedSession_loadsEverythingAnewAtLevelZero() = runBlocking<Unit> {
        val (main, duress) = mainWithDuress()
        enterUnderLock(DURESS_PASSWORD)
        graph.vault.awaitLevelRelease()
        val narrowed = session
        graph.activeWallets.value = listOf(wallet(native(BlockchainType.Dash), duress))
        awaitAdapter(graph.activeWallets.value.single())

        graph.vault.lock()
        assertEquals(UnlockOutcome.Success, graph.vault.unlock(password(MAIN_PASSWORD)))

        assertNull(narrowed.sharedNamesSecret())
        awaitAccounts(main, duress)
        assertEquals(0, userManager.getUserLevel())
        assertEquals(emptyList(), graph.activeWallets.value)
        withTimeout(WAIT_MS) {
            val offline = graph.koin.get<OfflineModeManager>()
            while (!offline.isNetworkPaused(OfflineKey(main.id, BlockchainType.Dash))) delay(POLL_MS)
        }
        val fresh = wallet(native(BlockchainType.Dogecoin), main)
        graph.activeWallets.value = listOf(fresh)
        assertNotNull(awaitAdapter(fresh))
    }

    @Test
    fun unlock_sameLevelAfterNarrowing_doesNotResetTheLevel() = runBlocking<Unit> {
        mainWithDuress()
        enterUnderLock(DURESS_PASSWORD)
        graph.vault.lock()

        assertEquals(UnlockOutcome.Success, graph.vault.unlock(password(DURESS_PASSWORD)))
        delay(SETTLE_MS)

        assertEquals(1, userManager.getUserLevel())
    }

    @Test
    fun unlock_responseOfWrongSameLevelAndNarrowing_differsLessThanTheArgon2Share() = runBlocking<Unit> {
        mainWithDuress()
        graph.vault.lock()
        val wrong = fastest { graph.vault.unlock(password("Not-a-password5")) }
        val sameLevel = fastest {
            graph.vault.unlock(password(MAIN_PASSWORD))
            graph.vault.lock()
        }

        val narrowing = timed { graph.vault.unlock(password(DURESS_PASSWORD)) }

        val tolerance = wrong / 2
        assertTrue(sameLevel < wrong + tolerance, "same level $sameLevel ms against wrong $wrong ms")
        assertTrue(narrowing < wrong + tolerance, "narrowing $narrowing ms against wrong $wrong ms")
    }

    @Test
    fun unlock_duress_newAccountBackupAlertOfTheLevelAboveIsGone() = runBlocking<Unit> {
        mainWithDuress()
        val unsaved = mnemonicAccount(isBackedUp = false)
        graph.accountManager.save(unsaved)
        assertEquals(unsaved.id, graph.accountManager.newAccountBackupRequiredFlow.value?.id)

        enterUnderLock(DURESS_PASSWORD)

        assertNull(graph.accountManager.newAccountBackupRequiredFlow.value)
    }

    @Test
    fun unlock_duress_premiumIsThatOfItsOwnWalletsAsOnAColdEntry() = runBlocking<Unit> {
        coEvery { graph.piratePlaceRepository.activateTrialPremium(any()) } returns TrialPremiumResult.DemoActive(7)
        val (main, duress) = mainWithDuress()
        assertIs<TrialPremiumResult.DemoActive>(graph.checkPremiumUseCase.activateTrialPremium(main.id))
        assertIs<TrialPremiumResult.DemoActive>(graph.checkPremiumUseCase.checkTrialPremiumStatus())

        enterUnderLock(DURESS_PASSWORD)

        val narrowed = graph.checkPremiumUseCase.checkTrialPremiumStatus()
        assertEquals(TrialPremiumResult.NeedPremium, narrowed)
        assertFalse(main.id in graph.checkPremiumUseCase.premiumTypesFlow.value)

        graph.koin.get<BeamSessionOwner>().close()
        graph.restart()
        assertEquals(UnlockOutcome.Success, graph.vault.unlock(password(DURESS_PASSWORD)))
        assertEquals(narrowed, graph.checkPremiumUseCase.checkTrialPremiumStatus())
        assertEquals(listOf(duress.id), graph.accountManager.accounts.map(Account::id))
    }

    @Test
    fun router_levelBelowTheNarrowedRoot_serveTheKnownCallersFromTheRoot() = runBlocking<Unit> {
        mainWithDuress()
        enterUnderLock(DURESS_PASSWORD)
        val router = graph.koin.get<StorageRouter>()

        graph.accountManager.setLevel(0)
        assertEquals(1, router.rootLevel)
        assertSame(router.level(1), router.level(0))
        graph.accountManager.setLevel(1)

        graph.checkPremiumUseCase.update()
        assertEquals(TrialPremiumResult.NeedPremium, graph.checkPremiumUseCase.checkTrialPremiumStatus())
    }

    /** The plan's inventory: after narrowing no singleton may still name an account of the level above. */
    @OptIn(KoinInternalApi::class)
    @Test
    fun narrowing_everySingletonOfTheGraph_keepsNoIdOfTheLevelAbove() = runBlocking<Unit> {
        val failures = CopyOnWriteArrayList<Throwable>()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, failure -> failures += failure }
        try {
            val (main, duress) = mainWithDuress()
            val mainWallets = KIT_TOKENS.map { it.second }
                .filterNot { it.blockchainType == BlockchainType.Dash }
                .map { wallet(it, main) }
            val kept = wallet(native(BlockchainType.Dash), duress)
            createEverySingleton()
            exerciseAccountStateOf(main, mainWallets)
            graph.activeWallets.value = mainWallets + kept
            awaitAdapter(kept)
            withTimeout(WAIT_MS) { adapterManager.initializationInProgressFlow.first { !it } }
            assertTrue(AccountIdScan(setOf(main.id)).scan(graph.koin).isNotEmpty(), "nothing to find before the change")

            enterUnderLock(DURESS_PASSWORD)
            graph.vault.awaitLevelRelease()
            delay(SETTLE_MS)

            assertEquals(emptyList(), AccountIdScan(setOf(main.id)).scan(graph.koin))
            assertEquals(emptyList(), failures.map(Throwable::toString))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    @Test
    fun unlock_mainOnDuressOff_amlFollowsEachLevelOnNarrowingAndColdSwitch() = runBlocking<Unit> {
        mainWithDuress()
        val aml = graph.koin.get<AmlStatusManager>()
        aml.setEnabled(true)

        enterUnderLock(DURESS_PASSWORD)
        assertFalse(aml.isEnabled)

        enterUnderLock(MAIN_PASSWORD)
        withTimeout(WAIT_MS) { aml.enabledStateFlow.first { it } }
    }

    @Test
    fun unlock_mainOffDuressOn_amlFollowsEachLevelOnNarrowingAndColdSwitch() = runBlocking<Unit> {
        mainWithDuress()
        val aml = graph.koin.get<AmlStatusManager>()
        enterUnderLock(DURESS_PASSWORD)
        aml.setEnabled(true)

        enterUnderLock(MAIN_PASSWORD)
        withTimeout(WAIT_MS) { aml.enabledStateFlow.first { !it } }

        enterUnderLock(DURESS_PASSWORD)
        assertTrue(aml.isEnabled)
    }

    @Test
    fun adapterFactory_accountOfTheLevelAbove_givesNoAdapterInsteadOfFailing() = runBlocking<Unit> {
        val (main, _) = mainWithDuress()
        val wallets = KIT_TOKENS.map { wallet(it.second, main) }
        enterUnderLock(DURESS_PASSWORD)
        graph.vault.awaitLevelRelease()

        val adapters = wallets.map { graph.koin.get<AdapterFactory>().getAdapterOrNull(it) }

        assertEquals(wallets.map { null }, adapters)
    }

    @Test
    fun coldSwitch_everySingletonOfTheGraph_dropsTheOldRootAndLoadsTheNewOne() = runBlocking<Unit> {
        val failures = CopyOnWriteArrayList<Throwable>()
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, failure -> failures += failure }
        try {
            val (main, _) = mainWithDuress()
            val hiddenRoot = hiddenRootWithAccount()
            enterUnderLock(MAIN_PASSWORD)
            val mainWallets = KIT_TOKENS.map { it.second }
                .filterNot { it.blockchainType == BlockchainType.Dash }
                .map { wallet(it, main) }
            createEverySingleton()
            exerciseAccountStateOf(main, mainWallets)
            graph.activeWallets.value = mainWallets
            awaitAdapter(mainWallets.first())
            withTimeout(WAIT_MS) { adapterManager.initializationInProgressFlow.first { !it } }
            assertTrue(AccountIdScan(setOf(main.id)).scan(graph.koin).isNotEmpty(), "nothing to find before the switch")

            enterUnderLock(HIDDEN_PASSWORD)
            awaitAccounts(hiddenRoot)
            delay(SETTLE_MS)

            assertEquals(emptyList(), AccountIdScan(setOf(main.id)).scan(graph.koin))
            assertEquals(emptyList(), failures.map(Throwable::toString))
            val offline = graph.koin.get<OfflineModeManager>()
            assertTrue(offline.isNetworkPaused(OfflineKey(hiddenRoot.id, BlockchainType.Dash)))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        }
    }

    /** Created under the upper level: an empty singleton built after the narrowing would prove nothing. */
    @OptIn(KoinInternalApi::class)
    private fun createEverySingleton() {
        val context = ResolutionContext(graph.koin.logger, graph.koin.scopeRegistry.rootScope, Any::class)
        graph.koin.instanceRegistry.instances.values.filterIsInstance<SingleInstanceFactory<*>>().forEach {
            try {
                it.get(context)
            } catch (e: Exception) {
                println("Singleton not created: ${it.beanDefinition.primaryType.simpleName}: ${e::class.simpleName}")
            }
        }
    }

    private fun exerciseAccountStateOf(main: Account, wallets: List<Wallet>) {
        val balanceHidden = graph.koin.get<IBalanceHiddenManager>()
        wallets.forEach { balanceHidden.toggleWalletBalanceHidden(it.tokenQueryId) }
        balanceHidden.transactionInfoHiddenFlowForWallet("${main.id}-transaction", wallets.first().tokenQueryId)
    }

    private fun holdBalanceResponse(response: CompletableDeferred<Unit>): AtomicInteger {
        val parked = AtomicInteger()
        coEvery { graph.koin.get<BinanceApi>().getTokenBalance(any(), any()) } returns null
        coEvery { graph.piratePlaceRepository.getInvestmentData(any(), any(), any()) } coAnswers {
            parked.incrementAndGet()
            try {
                response.await()
            } finally {
                parked.decrementAndGet()
            }
            InvestmentData(balance = "0", unrealizedValue = "0", mint = "0", stakingActive = false)
        }
        return parked
    }

    private suspend fun rowsOfRootPremiumAbout(accountId: String): Int =
        session.chain.root.premium.useReaderConnection { connection ->
            PREMIUM_TABLES_BY_ACCOUNT.sumOf { table ->
                connection.usePrepared("SELECT count(*) FROM $table WHERE accountId = ?") { statement ->
                    statement.bindText(1, accountId)
                    statement.step()
                    statement.getInt(0)
                }
            }
        }

    private suspend fun mainWithDuress(): Pair<Account, Account> {
        assertEquals(CreateOutcome.SUCCESS, graph.vault.create(password(MAIN_PASSWORD)))
        assertEquals(LevelOutcome.SUCCESS, graph.vault.setDuressPassword(password(DURESS_PASSWORD)))
        graph.followVisibleAccounts()
        val main = saved()
        val duress = saved()
        graph.koin.get<StorageRouter>().setLevel(listOf(duress.id), 1)
        // The account set changed under the manager: the same read a level change does.
        graph.accountManager.setLevel(0)
        return main to duress
    }

    private suspend fun hiddenRootWithAccount(): Account {
        assertEquals(LevelOutcome.SUCCESS, graph.vault.createHiddenWallet(password(HIDDEN_PASSWORD)))
        enterUnderLock(HIDDEN_PASSWORD)
        awaitAccounts()
        return saved()
    }

    private suspend fun saved(): Account = mnemonicAccount(isBackedUp = true).also { account ->
        graph.accountManager.save(account)
        (KIT_TOKENS.map { it.second }).map(Token::blockchainType).distinct().forEach {
            graph.koin.get<OfflineModeManager>().persistAndPublish(account.id, it, offline = true)
        }
    }

    private suspend fun enterUnderLock(password: String) {
        graph.vault.lock()
        assertEquals(UnlockOutcome.Success, graph.vault.unlock(password(password)))
    }

    private fun wallet(token: Token, account: Account): Wallet =
        requireNotNull(graph.koin.get<WalletFactory>().create(token, account, null))

    private suspend fun awaitAdapter(wallet: Wallet): IAdapter = withTimeout(WAIT_MS) {
        var adapter = adapterManager.getAdapterForWallet<IAdapter>(wallet)
        while (adapter == null) {
            delay(POLL_MS)
            adapter = adapterManager.getAdapterForWallet(wallet)
        }
        adapter
    }

    private suspend fun awaitAccounts(vararg accounts: Account) = withTimeout(WAIT_MS) {
        val expected = accounts.map(Account::id).toSet()
        while (graph.accountManager.accounts.map(Account::id).toSet() != expected) delay(POLL_MS)
    }

    private suspend fun fastest(attempt: suspend () -> Unit): Long = List(SAMPLES) { timed(attempt) }.min()

    private suspend fun timed(attempt: suspend () -> Unit): Long {
        val start = System.nanoTime()
        attempt()
        return (System.nanoTime() - start) / NANOS_PER_MS
    }

    private companion object {
        const val WAIT_MS = 60_000L
        const val POLL_MS = 20L
        const val SETTLE_MS = 500L
        const val SAMPLES = 2
        const val NANOS_PER_MS = 1_000_000L
        val PREMIUM_TABLES_BY_ACCOUNT = listOf("premium_users", "account_premium_cache", "bnb_premium_address")
    }
}
