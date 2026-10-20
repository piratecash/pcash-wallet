package cash.p.terminal.modules.manageaccount

import cash.p.terminal.core.adapters.zcash.ZcashKeyExporter
import cash.p.terminal.core.adapters.zcash.ZcashPrivateKeyType
import cash.p.terminal.modules.manageaccount.ManageAccountModule.KeyAction
import cash.p.terminal.wallet.AccountType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class ManageAccountKeyActionsTest {

    private val exporter = mockk<ZcashKeyExporter>()

    @Test
    fun zcashKeyActions_saplingSpendingKeyWithDerivableViewingKey_offersBothScreens() = runTest {
        val type = AccountType.ZCashSaplingKey(SPENDING_KEY)
        every { exporter.privateKeyTypes(type) } returns listOf(ZcashPrivateKeyType.Shielded)
        coEvery { exporter.viewingKey(type) } returns VIEWING_KEY

        assertEquals(
            listOf(KeyAction.PrivateKeys, KeyAction.PublicKeys),
            zcashKeyActions(type, exporter)
        )
    }

    @Test
    fun zcashKeyActions_saplingSpendingKeyWhoseDerivationFails_offersPrivateKeysOnly() = runTest {
        val type = AccountType.ZCashSaplingKey(SPENDING_KEY)
        every { exporter.privateKeyTypes(type) } returns listOf(ZcashPrivateKeyType.Shielded)
        coEvery { exporter.viewingKey(type) } returns null

        assertEquals(listOf(KeyAction.PrivateKeys), zcashKeyActions(type, exporter))
    }

    @Test
    fun zcashKeyActions_saplingViewingKey_offersPublicKeysOnly() = runTest {
        val type = AccountType.ZCashSaplingKey(VIEWING_KEY)
        every { exporter.privateKeyTypes(type) } returns emptyList()
        coEvery { exporter.viewingKey(type) } returns VIEWING_KEY

        assertEquals(listOf(KeyAction.PublicKeys), zcashKeyActions(type, exporter))
    }

    @Test
    fun zcashKeyActions_unifiedFullViewingKey_offersPublicKeysOnly() = runTest {
        val type = AccountType.ZCashUfvKey(UFVK)
        every { exporter.privateKeyTypes(type) } returns emptyList()
        coEvery { exporter.viewingKey(type) } returns UFVK

        assertEquals(listOf(KeyAction.PublicKeys), zcashKeyActions(type, exporter))
    }

    private companion object {
        const val SPENDING_KEY = "secret-extended-key-main1qsaplingspendingkey"
        const val VIEWING_KEY = "zxviews1qsaplingviewingkey"
        const val UFVK = "uview1qaccountkey"
    }
}
