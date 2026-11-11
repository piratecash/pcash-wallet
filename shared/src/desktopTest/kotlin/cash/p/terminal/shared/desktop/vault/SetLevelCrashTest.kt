package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.DURESS_ACCOUNT
import kotlin.test.Test
import kotlin.test.assertEquals

class SetLevelCrashTest {

    @Test
    fun setLevel_crashAfterEachStep_accountVisibleFromOneLevelAndDataIntact() {
        listOf(
            // A duplicate resolves to the lower level: the move is lost at most, never the account.
            Crash(from = 0, to = 1, StorageCheckpoint.SET_LEVEL_POINTERS_INSERTED, expectedLevel = 1),
            Crash(from = 0, to = 1, StorageCheckpoint.SET_LEVEL_POINTERS_DELETED_FROM_SAFE, expectedLevel = 1),
            Crash(from = 1, to = 0, StorageCheckpoint.SET_LEVEL_POINTERS_INSERTED, expectedLevel = 1),
            Crash(from = 1, to = 0, StorageCheckpoint.SET_LEVEL_POINTERS_DELETED_FROM_SAFE, expectedLevel = 0),
        ).forEach(::assertRecovers)
    }

    private fun assertRecovers(crash: Crash) = levelsScenario { fixture ->
        fixture.create()
        fixture.setDuress()
        fixture.addAccount(DURESS_ACCOUNT)
        fixture.writeKitRows(DURESS_ACCOUNT, "before")
        fixture.router.setLevel(listOf(DURESS_ACCOUNT), crash.from)
        fixture.checkpoints.crashAt(crash.step)

        fixture.assertCrashes { fixture.router.setLevel(listOf(DURESS_ACCOUNT), crash.to) }

        fixture.reloginOk()
        assertEquals(mapOf(DURESS_ACCOUNT to crash.expectedLevel), fixture.visibleAccounts(), "$crash")
        assertEquals(1, fixture.pointerCount(DURESS_ACCOUNT), "$crash")
        assertEquals(1, fixture.store(DURESS_ACCOUNT).walletsDao().enabledCoins(DURESS_ACCOUNT).size, "$crash")
        fixture.reloginOk(DURESS_PASSWORD)
        assertEquals(crash.expectedLevel == 1, DURESS_ACCOUNT in fixture.visibleAccounts(), "$crash")
    }

    private data class Crash(val from: Int, val to: Int, val step: StorageCheckpoint, val expectedLevel: Int)
}
