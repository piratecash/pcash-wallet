package cash.p.terminal.modules.enablecoin.restoresettings

import cash.p.terminal.core.managers.ZcashBirthdayProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ZcashRestoreHeightTest {

    private val zcashBirthdayProvider = mockk<ZcashBirthdayProvider> {
        every { getLatestCheckpointBlockHeight() } returns CHECKPOINT
    }

    @Test
    fun zcashBirthdayHeight_existingWalletWithHeight_usesTheEnteredHeight() {
        val config = TokenConfig(birthdayHeight = "3100000", restoreAsNew = false)

        assertEquals(3_100_000L, config.zcashBirthdayHeight(zcashBirthdayProvider))
    }

    @Test
    fun zcashBirthdayHeight_newWalletWithHeight_usesTheEnteredHeight() {
        val config = TokenConfig(birthdayHeight = "3100000", restoreAsNew = true)

        assertEquals(3_100_000L, config.zcashBirthdayHeight(zcashBirthdayProvider))
    }

    @Test
    fun zcashBirthdayHeight_newWalletWithoutHeight_usesTheLatestCheckpoint() {
        val config = TokenConfig(birthdayHeight = null, restoreAsNew = true)

        assertEquals(CHECKPOINT, config.zcashBirthdayHeight(zcashBirthdayProvider))
    }

    @Test
    fun zcashBirthdayHeight_existingWalletWithoutHeight_leavesTheHeightUnset() {
        val config = TokenConfig(birthdayHeight = null, restoreAsNew = false)

        assertNull(config.zcashBirthdayHeight(zcashBirthdayProvider))
    }

    private companion object {
        const val CHECKPOINT = 3_424_810L
    }
}
