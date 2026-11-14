package cash.p.terminal.shared.desktop

import cash.p.terminal.shared.desktop.vault.DesktopKitPlatformContexts
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.DURESS_ACCOUNT
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.MAIN_ACCOUNT
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DesktopTonConnectStorageTest {
    private val fixture = VaultLevelsFixture()
    private val accountManager = mockk<IAccountManager>()
    private val storage
        get() = DesktopTonConnectStorage(
            fixture.vault,
            DesktopKitPlatformContexts(fixture.files, fixture.vault),
            accountManager,
        )

    @AfterTest
    fun tearDown() = runBlocking { fixture.tearDown() }

    @Test
    fun isCreated_afterTheFirstKeyOfTheAccount_trueForItOnly() = runBlocking<Unit> {
        fixture.mainWithDuress()
        assertFalse(storage.isCreated(MAIN_ACCOUNT))

        assertTrue(storage.databaseKey(MAIN_ACCOUNT).isNew)

        assertTrue(storage.isCreated(MAIN_ACCOUNT))
        assertFalse(storage.isCreated(DURESS_ACCOUNT))
    }

    @Test
    fun isCreated_afterRelogin_staysTrueWithTheSameKey() = runBlocking<Unit> {
        fixture.mainWithDuress()
        val key = storage.databaseKey(MAIN_ACCOUNT)

        fixture.reloginOk()

        assertTrue(storage.isCreated(MAIN_ACCOUNT))
        val again = storage.databaseKey(MAIN_ACCOUNT)
        assertFalse(again.isNew)
        assertTrue(key.bytes.contentEquals(again.bytes))
    }

    @Test
    fun nameResolver_eachAccount_hidesTheKitNameUnderItsOwnName() = runBlocking<Unit> {
        fixture.mainWithDuress()
        val name = storage.nameResolver(MAIN_ACCOUNT).resolve(KIT_DATABASE_NAME)

        assertNotEquals(KIT_DATABASE_NAME, name)
        assertNotEquals(name, storage.nameResolver(DURESS_ACCOUNT).resolve(KIT_DATABASE_NAME))
    }

    @Test
    fun liveScopes_accountWithoutTonConnect_isLeftOut() {
        every { accountManager.accounts } returns listOf(
            account(MAIN_ACCOUNT, mockk<AccountType.Mnemonic>()),
            account(DURESS_ACCOUNT, AccountType.EvmAddress("0x01")),
        )

        assertEquals(setOf(MAIN_ACCOUNT), storage.liveScopes())
    }

    private fun account(id: String, accountType: AccountType) = mockk<Account> {
        every { this@mockk.id } returns id
        every { type } returns accountType
    }

    private companion object {
        const val KIT_DATABASE_NAME = "ton-connect"
    }
}
