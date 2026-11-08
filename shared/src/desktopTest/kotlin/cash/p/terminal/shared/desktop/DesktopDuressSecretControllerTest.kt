package cash.p.terminal.shared.desktop

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.DefaultUserManager
import cash.p.terminal.modules.pin.LevelOutcome
import cash.p.terminal.shared.desktop.vault.DesktopVaultState
import cash.p.terminal.shared.desktop.vault.DURESS_PASSWORD
import cash.p.terminal.shared.desktop.vault.HIDDEN_PASSWORD
import cash.p.terminal.shared.desktop.vault.MAIN_PASSWORD
import cash.p.terminal.shared.desktop.vault.NESTED_PASSWORD
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture
import cash.p.terminal.shared.desktop.vault.levelsScenario
import cash.p.terminal.shared.main.MainDestination
import cash.p.terminal.shared.main.MainTabStorage
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopDuressSecretControllerTest {

    private val mainTabStorage = object : MainTabStorage {
        override var mainTab: MainDestination? = null
    }
    private val hiddenWalletEntry = DesktopHiddenWalletEntry(mainTabStorage)

    private fun VaultLevelsFixture.controller(): DesktopDuressSecretController {
        val userManager = DefaultUserManager(mockk(relaxed = true), router).apply { initUserLevel(session.rootLevel) }
        return DesktopDuressSecretController(
            vault, userManager, hiddenWalletEntry,
            TestDispatcherProvider(Dispatchers.Unconfined, CoroutineScope(Dispatchers.Unconfined)),
        )
    }

    @Test
    fun setDuressSecret_inMain_makesDuressSetAndAvailable() = levelsScenario { fixture ->
        fixture.create()
        val controller = fixture.controller()
        assertFalse(controller.isDuressSet())

        assertEquals(LevelOutcome.SUCCESS, controller.setDuressSecret(DURESS_PASSWORD.toCharArray()))

        assertTrue(controller.isDuressSet())
        assertTrue(controller.isDuressAvailable())
    }

    @Test
    fun setDuressSecret_mainPassword_isInUse() = levelsScenario { fixture ->
        fixture.create()

        val outcome = fixture.controller().setDuressSecret(MAIN_PASSWORD.toCharArray())

        assertEquals(LevelOutcome.PASSWORD_IN_USE, outcome)
    }

    @Test
    fun setDuressSecret_insideDuress_createsTheNestedLevel() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        fixture.reloginOk(DURESS_PASSWORD)
        val controller = fixture.controller()

        assertEquals(LevelOutcome.SUCCESS, controller.setDuressSecret(NESTED_PASSWORD.toCharArray()))

        assertTrue(controller.isDuressSet())
        fixture.reloginOk(NESTED_PASSWORD)
    }

    @Test
    fun disableDuress_duressSet_removesItAndAllowsANewOne() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        val controller = fixture.controller()

        controller.disableDuress()

        assertFalse(controller.isDuressSet())
        assertEquals(LevelOutcome.SUCCESS, controller.setDuressSecret(DURESS_PASSWORD.toCharArray()))
    }

    @Test
    fun createHiddenWallet_newPassword_entersItAndAsksForTheFirstWalletOnce() = levelsScenario { fixture ->
        fixture.create()
        val controller = fixture.controller()
        val secret = HIDDEN_PASSWORD.toCharArray()

        assertEquals(LevelOutcome.SUCCESS, controller.createHiddenWallet(secret))

        assertIs<DesktopVaultState.Unlocked>(fixture.vault.state.value)
        assertTrue(fixture.session.chain.root.meta.isHiddenRoot)
        assertEquals(MainDestination.Balance, mainTabStorage.mainTab)
        val rootSlot = fixture.session.rootSlot
        assertTrue(hiddenWalletEntry.consume(rootSlot))
        assertFalse(hiddenWalletEntry.consume(rootSlot))
        assertContentEquals(CharArray(secret.size), secret)
    }

    @Test
    fun createHiddenWallet_insideTheNewWallet_hidesDuress() = levelsScenario { fixture ->
        fixture.create()
        val controller = fixture.controller()
        controller.createHiddenWallet(HIDDEN_PASSWORD.toCharArray())

        assertFalse(controller.isDuressAvailable())
        assertFalse(controller.isDuressSet())
    }

    @Test
    fun createHiddenWallet_emptyWalletPasswordAgain_entersTheSameWallet() = levelsScenario { fixture ->
        fixture.create()
        fixture.createHidden()
        fixture.reloginOk()
        val controller = fixture.controller()

        assertEquals(LevelOutcome.SUCCESS, controller.createHiddenWallet(HIDDEN_PASSWORD.toCharArray()))

        assertTrue(fixture.session.chain.root.meta.isHiddenRoot)
    }

    @Test
    fun createHiddenWallet_mainPassword_isInUseAndStaysInMain() = levelsScenario { fixture ->
        fixture.create()
        val controller = fixture.controller()

        assertEquals(LevelOutcome.PASSWORD_IN_USE, controller.createHiddenWallet(MAIN_PASSWORD.toCharArray()))

        assertFalse(fixture.session.chain.root.meta.isHiddenRoot)
        assertFalse(hiddenWalletEntry.consume(fixture.session.rootSlot))
    }

    @Test
    fun createHiddenWallet_placesExhausted_reportsTheLimit() = levelsScenario { fixture ->
        fixture.create()
        var index = 0
        var outcome: LevelOutcome
        do {
            fixture.reloginOk()
            outcome = fixture.controller().createHiddenWallet("Hidden-password-${index++}".toCharArray())
        } while (outcome == LevelOutcome.SUCCESS)

        assertEquals(LevelOutcome.LIMIT_REACHED, outcome)
        assertTrue(index > 1)
    }
}
