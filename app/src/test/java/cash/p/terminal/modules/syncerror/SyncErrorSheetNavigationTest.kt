package cash.p.terminal.modules.syncerror

import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.modules.main.PlainTestPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.wallet.Wallet
import io.mockk.mockk
import org.junit.Assert.assertSame
import org.junit.Test

class SyncErrorSheetNavigationTest {

    private val root = PlainTestPage()
    private val navigation = HSNavigation(NavBackStack<HSPage>(root))
    private val wallet = mockk<Wallet>()

    @Test
    fun closeSyncErrorDialogUnlessFailing_walletNoLongerFailing_closesSheet() {
        navigation.slideFromBottom(sheetFor(wallet))

        navigation.closeSyncErrorDialogUnlessFailing(emptyList())

        assertSame(root, navigation.lastOrNull())
    }

    @Test
    fun closeSyncErrorDialogUnlessFailing_walletStillFailing_keepsSheet() {
        val sheet = sheetFor(wallet)
        navigation.slideFromBottom(sheet)

        navigation.closeSyncErrorDialogUnlessFailing(listOf(wallet))

        assertSame(sheet, navigation.lastOrNull())
    }

    @Test
    fun closeSyncErrorDialogUnlessFailing_otherPageOnTop_keepsIt() {
        val page = PlainTestPage()
        navigation.slideFromRight(page)

        navigation.closeSyncErrorDialogUnlessFailing(emptyList())

        assertSame(page, navigation.lastOrNull())
    }

    private fun sheetFor(wallet: Wallet) = SyncErrorSheet(
        SyncErrorSheet.Input(wallet, errorMessage = "error", isBlockedByProvider = false, isDeviceError = true)
    )
}
