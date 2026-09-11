package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.core.ILocalStorage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZcashIronwoodMigrationRegistryTest {

    private var stored = emptySet<String>()

    private val localStorage = mockk<ILocalStorage>(relaxed = true) {
        every { zcashIronwoodMigrationTxIds } answers { stored }
        every { zcashIronwoodMigrationTxIds = any() } answers { stored = firstArg() }
    }

    @Test
    fun contains_afterProcessRestart_stillRecognizesTheMigration() = runTest {
        ZcashIronwoodMigrationRegistry(localStorage).remember(ACCOUNT_ID, listOf(TX_HASH))

        val afterRestart = ZcashIronwoodMigrationRegistry(localStorage)

        assertTrue(afterRestart.contains(ACCOUNT_ID, TX_HASH))
    }

    @Test
    fun contains_hashWrittenInAnotherNotation_recognizesTheMigration() = runTest {
        val registry = ZcashIronwoodMigrationRegistry(localStorage)
        registry.remember(ACCOUNT_ID, listOf("0x" + TX_HASH.uppercase()))

        assertTrue(registry.contains(ACCOUNT_ID, TX_HASH))
    }

    @Test
    fun contains_anotherAccount_doesNotRecognizeTheMigration() = runTest {
        val registry = ZcashIronwoodMigrationRegistry(localStorage)
        registry.remember(ACCOUNT_ID, listOf(TX_HASH))

        assertFalse(registry.contains("other-account", TX_HASH))
    }

    @Test
    fun remember_secondMigration_keepsThePreviousOne() = runTest {
        val registry = ZcashIronwoodMigrationRegistry(localStorage)
        registry.remember(ACCOUNT_ID, listOf(TX_HASH))
        registry.remember(ACCOUNT_ID, listOf(OTHER_TX_HASH))

        assertTrue(registry.contains(ACCOUNT_ID, TX_HASH))
        assertTrue(registry.contains(ACCOUNT_ID, OTHER_TX_HASH))
    }

    private companion object {
        const val ACCOUNT_ID = "test-account-id"
        const val TX_HASH = "b7c4a1f0d3e2b5a698877665544332211ffeeddccbbaa99887766554433221100"
        const val OTHER_TX_HASH = "00112233445566778899aabbccddeeff11223344556677889900aabbccddeeff"
    }
}
