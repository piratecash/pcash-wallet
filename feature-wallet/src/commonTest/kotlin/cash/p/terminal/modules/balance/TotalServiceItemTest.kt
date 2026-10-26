package cash.p.terminal.modules.balance

import cash.p.terminal.core.NotSupportedOnPlatformException
import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.balance.BalanceItem
import cash.p.terminal.wallet.entities.BalanceData
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class TotalServiceItemTest {

    @Test
    fun toTotalServiceItem_unsupportedNextToSyncedWallet_totalNotPending() {
        val items = listOf(
            balanceItem(AdapterState.NotSynced(NotSupportedOnPlatformException())),
            balanceItem(AdapterState.Synced),
        )

        assertEquals(listOf(false, false), items.map { it.toTotalServiceItem().isValuePending })
    }

    @Test
    fun toTotalServiceItem_notSyncedForAnotherReason_staysPending() {
        val item = balanceItem(AdapterState.NotSynced(IllegalStateException("offline")))

        assertEquals(true, item.toTotalServiceItem().isValuePending)
    }

    private fun balanceItem(state: AdapterState) = BalanceItem(
        wallet = mockk(relaxed = true),
        balanceData = BalanceData(BigDecimal.ONE),
        state = state,
        sendAllowed = true,
        coinPrice = null,
    )
}
