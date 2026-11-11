package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.IRestoreSettingsStorage
import cash.p.terminal.core.storage.RestoreSettingsStorage
import cash.p.terminal.core.storage.observeAcrossAccounts
import cash.p.terminal.entities.PendingMultiSwap
import cash.p.terminal.entities.RestoreSettingRecord
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.DURESS_ACCOUNT
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.MAIN_ACCOUNT
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.NESTED_ACCOUNT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultLevelVisibilityTest {
    private val fixture = VaultLevelsFixture()

    @AfterTest
    fun tearDown() = runBlocking { fixture.tearDown() }

    @Test
    fun allAccounts_eachPassword_seesItsLevelAndLowerOnly() = runBlocking<Unit> {
        fixture.mainWithNestedDuress()
        assertEquals(
            mapOf(MAIN_ACCOUNT to 0, DURESS_ACCOUNT to 1, NESTED_ACCOUNT to 2),
            fixture.visibleAccounts(),
        )

        fixture.reloginOk(DURESS_PASSWORD)
        assertEquals(mapOf(DURESS_ACCOUNT to 0, NESTED_ACCOUNT to 1), fixture.visibleAccounts())
        assertNull(fixture.router.account(MAIN_ACCOUNT))

        fixture.reloginOk(NESTED_PASSWORD)
        assertEquals(mapOf(NESTED_ACCOUNT to 0), fixture.visibleAccounts())
        assertNull(fixture.router.account(DURESS_ACCOUNT))
    }

    @Test
    fun create_unlockWithTheCreatingPassword_opensASingleLevelEndToEnd() = runBlocking<Unit> {
        fixture.create()
        fixture.addAccount(MAIN_ACCOUNT)

        fixture.reloginOk()

        assertEquals(mapOf(MAIN_ACCOUNT to 0), fixture.visibleAccounts())
        assertEquals(1, fixture.session.chain.safes.size)
    }

    @Test
    fun setLevel_allowEditDisallow_leaveTheAccountStoresByteIdentical() = runBlocking<Unit> {
        fixture.create()
        fixture.setDuress()
        fixture.addAccount(MAIN_ACCOUNT)
        fixture.addAccount(DURESS_ACCOUNT)
        val before = closedStoreHashes()

        fixture.router.setLevel(listOf(MAIN_ACCOUNT, DURESS_ACCOUNT), 1)
        fixture.router.setLevel(listOf(MAIN_ACCOUNT), 0)
        fixture.router.collapseLevelsAbove(0)

        assertEquals(before, closedStoreHashes())
        assertEquals(mapOf(MAIN_ACCOUNT to 0, DURESS_ACCOUNT to 0), fixture.visibleAccounts())
    }

    @Test
    fun clearDeleted_accountDeletedInDuressWithDuplicatePointer_doesNotReturnInMain() = runBlocking<Unit> {
        fixture.create()
        fixture.setDuress()
        fixture.addAccount(MAIN_ACCOUNT)
        fixture.addAccount(DURESS_ACCOUNT)
        fixture.checkpoints.crashAt(StorageCheckpoint.SET_LEVEL_POINTERS_INSERTED)
        fixture.assertCrashes { fixture.router.setLevel(listOf(DURESS_ACCOUNT), 1) }

        fixture.reloginOk(DURESS_PASSWORD)
        fixture.accountsStorage.delete(DURESS_ACCOUNT)
        fixture.accountsStorage.clearDeleted(listOf(DURESS_ACCOUNT))
        assertNull(fixture.router.account(DURESS_ACCOUNT))

        fixture.reloginOk()
        assertEquals(mapOf(MAIN_ACCOUNT to 0), fixture.visibleAccounts())
        assertNull(fixture.router.levelOf(DURESS_ACCOUNT))
        assertEquals(1, fixture.accountStoreDirectories())
    }

    @Test
    fun prepareAccountStore_restoreSettingsWrittenBeforeTheRow_surviveAndAccountAppearsOnlyWithItsRow() =
        runBlocking<Unit> {
            fixture.create()
            val settings: IRestoreSettingsStorage = RestoreSettingsStorage(fixture.router)

            settings.prepareForNewAccount(MAIN_ACCOUNT)
            settings.save(listOf(RestoreSettingRecord(MAIN_ACCOUNT, "zcash", "birthdayHeight", "42")))
            assertTrue(fixture.visibleAccounts().isEmpty())
            fixture.addAccount(MAIN_ACCOUNT)

            fixture.reloginOk()
            assertEquals(mapOf(MAIN_ACCOUNT to 0), fixture.visibleAccounts())
            val restored = RestoreSettingsStorage(fixture.router).restoreSettings(MAIN_ACCOUNT)
            assertEquals(listOf("42"), restored.map { it.value })
        }

    @Test
    fun openAll_storeWithNoAccountRow_isDroppedWithItsPointer() = runBlocking<Unit> {
        fixture.create()
        fixture.router.prepareAccountStore(MAIN_ACCOUNT)
        assertEquals(1, fixture.pointerCount(MAIN_ACCOUNT))

        fixture.reloginOk()

        assertNull(fixture.router.account(MAIN_ACCOUNT))
        assertEquals(0, fixture.pointerCount(MAIN_ACCOUNT))
        assertEquals(0, fixture.accountStoreDirectories())
    }

    @Test
    fun prepareAccountStore_concurrentCallsForOneAccount_createOneStore() = runBlocking<Unit> {
        fixture.create()

        List(8) { async(Dispatchers.IO) { fixture.router.prepareAccountStore(MAIN_ACCOUNT) } }.awaitAll()

        assertEquals(1, fixture.pointerCount(MAIN_ACCOUNT))
        assertEquals(1, fixture.accountStoreDirectories())
    }

    @Test
    fun observeAcrossAccounts_accountStoreAddedWhileSubscribed_emitsItsRows() = runBlocking<Unit> {
        fixture.create()
        fixture.addAccount(MAIN_ACCOUNT)
        val rows = fixture.router.observeAcrossAccounts { pendingMultiSwapDao().getByAccountId(DURESS_ACCOUNT) }
        val seen = async(Dispatchers.IO) { withTimeout(TIMEOUT_MS) { rows.first { it.isNotEmpty() } } }

        fixture.addAccount(DURESS_ACCOUNT)
        fixture.store(DURESS_ACCOUNT).pendingMultiSwapDao().insert(pendingSwap(DURESS_ACCOUNT))

        assertEquals(listOf(DURESS_ACCOUNT), seen.await().map { it.accountId })
    }

    @Test
    fun account_emptyId_isNoStoreSoARowWithoutAccountIsSkipped() = runBlocking<Unit> {
        fixture.create()
        fixture.addAccount(MAIN_ACCOUNT)

        assertNull(fixture.router.account(""))
        assertEquals(1, fixture.router.accounts.size)
    }

    @Test
    fun level_belowTheSessionRoot_isServedByTheRoot() = runBlocking<Unit> {
        fixture.create()
        fixture.accountsStorage.setActiveAccountId(0, MAIN_ACCOUNT)

        assertEquals(MAIN_ACCOUNT, fixture.accountsStorage.getActiveAccountId(-1))
        assertNotNull(DesktopPremiumDatabaseRouter(fixture.vault).forLevel(-1))
    }

    @Test
    fun activeAccount_setInDuressDirectly_isTheOneTheChainReadsAtItsLevel() = runBlocking<Unit> {
        fixture.mainWithDuress()
        fixture.reloginOk(DURESS_PASSWORD)
        fixture.accountsStorage.setActiveAccountId(0, DURESS_ACCOUNT)

        fixture.reloginOk()

        assertEquals(DURESS_ACCOUNT, fixture.accountsStorage.getActiveAccountId(1))
        assertNull(fixture.accountsStorage.getActiveAccountId(0))
    }

    private suspend fun closedStoreHashes(): Map<String, String> {
        fixture.reloginOk()
        return fixture.accountStoreHashes().filterKeys { !it.endsWith("-wal") && !it.endsWith("-shm") }
    }

    private fun pendingSwap(accountId: String) = PendingMultiSwap(
        id = "swap-$accountId",
        accountId = accountId,
        createdAt = 1L,
        coinUidIn = "bitcoin",
        blockchainTypeIn = "bitcoin",
        amountIn = BigDecimal.ONE,
        coinUidIntermediate = "tether",
        blockchainTypeIntermediate = "ethereum",
        coinUidOut = "monero",
        blockchainTypeOut = "monero",
        leg1ProviderId = "provider",
        leg1IsOffChain = false,
        leg1TransactionId = null,
        leg1AmountOut = null,
        leg1Status = PendingMultiSwap.STATUS_PENDING,
        leg2ProviderId = null,
        leg2IsOffChain = null,
        leg2TransactionId = null,
        leg2AmountOut = null,
        leg2Status = PendingMultiSwap.STATUS_PENDING,
        expectedAmountOut = BigDecimal.ONE,
    )

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
