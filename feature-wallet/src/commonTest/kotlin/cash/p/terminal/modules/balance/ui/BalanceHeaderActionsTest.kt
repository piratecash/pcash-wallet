package cash.p.terminal.modules.balance.ui

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.modules.balance.BackupRequirementState
import cash.p.terminal.modules.manageaccount.dialogs.BackupRequiredSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.ui_compose.components.HudMessenger
import cash.p.terminal.wallet.zcashMnemonicAccount
import cash.p.terminal.wallet.policy.PlatformOperation
import cash.p.terminal.wallet.policy.PlatformOperationSupport
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceHeaderActionsTest {

    private val root = StubPage()
    private val backStack = NavBackStack<HSPage>(root)
    private val navigation = HSNavigation(backStack)
    private val hud = mockk<HudMessenger>(relaxed = true)
    private val operationSupport = mockk<PlatformOperationSupport>()

    @Test
    fun openIfSupported_sendUnsupported_showsHudWithoutNavigation() {
        every { operationSupport.supports(PlatformOperation.Send) } returns false

        navigation.openIfSupported(operationSupport, PlatformOperation.Send, hud) { StubPage() }

        verify(exactly = 1) { hud.showWarning(any(), any()) }
        assertEquals(listOf<HSPage>(root), backStack.toList())
    }

    @Test
    fun openIfSupported_swapUnsupported_showsHudWithoutNavigation() {
        every { operationSupport.supports(PlatformOperation.Swap) } returns false

        navigation.openIfSupported(operationSupport, PlatformOperation.Swap, hud) { StubPage() }

        verify(exactly = 1) { hud.showWarning(any(), any()) }
        assertEquals(listOf<HSPage>(root), backStack.toList())
    }

    @Test
    fun openIfSupported_sendSupported_opensPage() {
        every { operationSupport.supports(PlatformOperation.Send) } returns true
        val sendPage = StubPage()

        navigation.openIfSupported(operationSupport, PlatformOperation.Send, hud) { sendPage }

        verify(exactly = 0) { hud.showWarning(any(), any()) }
        assertEquals(listOf(root, sendPage), backStack.toList())
    }

    @Test
    fun openSwap_unsupportedBackupRequired_showsHudWithoutBackupPrompt() {
        every { operationSupport.supports(PlatformOperation.Swap) } returns false
        val backupRequired = BackupRequirementState.BackupRequired(zcashMnemonicAccount())

        navigation.openSwap(operationSupport, backupRequired, hud) { StubPage() }

        verify(exactly = 1) { hud.showWarning(any(), any()) }
        assertEquals(listOf<HSPage>(root), backStack.toList())
    }

    @Test
    fun openSwap_supportedBackupRequired_opensBackupPrompt() {
        every { operationSupport.supports(PlatformOperation.Swap) } returns true
        val backupRequired = BackupRequirementState.BackupRequired(zcashMnemonicAccount())

        navigation.openSwap(operationSupport, backupRequired, hud) { StubPage() }

        verify(exactly = 0) { hud.showWarning(any(), any()) }
        assertTrue(backStack.last() is BackupRequiredSheet)
    }

    private class StubPage : HSPage() {
        @Composable
        override fun GetContent(navigation: HSNavigation) = Unit
    }
}
