package cash.p.terminal.shared.desktop

import cash.p.terminal.modules.activatetoken.ActivateTokenPage
import cash.p.terminal.modules.send.SendPage
import cash.p.terminal.modules.sendtokenselect.PrefilledData
import cash.p.terminal.modules.sendtokenselect.SendTokenSelectPage
import cash.p.terminal.modules.transactionInfo.options.SpeedUpCancelType
import cash.p.terminal.modules.transactionInfo.options.TransactionSpeedUpCancelPage
import cash.p.terminal.modules.transactionInfo.resendbitcoin.ResendBitcoinPage
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.policy.PlatformOperation
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.ui.dialogs.ConfirmationDialogSheet
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopSendWiringTest {

    private val pages = DesktopWalletPages(mockk(relaxed = true))

    @Test
    fun send_wallet_opensSendPageForThatWallet() {
        val wallet = mockk<Wallet>()
        val prefilled = PrefilledData(address = "addr")

        val page = assertIs<SendPage>(pages.send(wallet, "Send", SendTokenSelectPage::class, prefilled))

        assertEquals(SendPage.Input(wallet, "Send", SendTokenSelectPage::class, prefilled), page.input)
    }

    @Test
    fun sendTokenSelect_noPreselection_opensTokenSelectPage() {
        assertIs<SendTokenSelectPage>(pages.sendTokenSelect(null))
    }

    @Test
    fun supports_send_returnsTrue() {
        assertTrue(DesktopPlatformOperationSupport.supports(PlatformOperation.Send))
    }

    @Test
    fun supports_transactionActions_returnsTrue() {
        listOf(PlatformOperation.SpeedUpCancel, PlatformOperation.Resend, PlatformOperation.ZcashMigration)
            .forEach { assertTrue(DesktopPlatformOperationSupport.supports(it), "$it") }
    }

    @Test
    fun speedUpCancel_evmTransaction_opensSpeedUpPageForIt() {
        val page = assertIs<TransactionSpeedUpCancelPage>(
            pages.speedUpCancel(BlockchainType.Ethereum, SpeedUpCancelType.SpeedUp, "0xhash", mockk())
        )

        assertEquals(
            TransactionSpeedUpCancelPage.Input(BlockchainType.Ethereum, SpeedUpCancelType.SpeedUp, "0xhash"),
            page.input,
        )
    }

    @Test
    fun resendBitcoin_type_opensResendPage() {
        val page = assertIs<ResendBitcoinPage>(pages.resendBitcoin(SpeedUpCancelType.SpeedUp))

        assertEquals(SpeedUpCancelType.SpeedUp, page.input.optionType)
    }

    @Test
    fun activateToken_wallet_opensActivatePageForThatWallet() {
        val wallet = mockk<Wallet>()

        assertEquals(wallet, assertIs<ActivateTokenPage>(pages.activateToken(wallet)).input)
    }

    @Test
    fun confirmation_beamCancelOrZcashRescan_opensConfirmationSheet() {
        assertIs<ConfirmationDialogSheet>(pages.confirmation("Title", "Warning", "Confirm", "Cancel") {})
    }
}
