package cash.p.terminal.core.usecase

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.TransactionSettingsStorage
import cash.p.terminal.core.adapters.BitcoinReplacementLink
import cash.p.terminal.core.adapters.BitcoinReplacementRegistry
import cash.p.terminal.core.managers.LocallyCreatedTransactionRepository
import cash.p.terminal.entities.transactionrecords.bitcoin.BitcoinTransactionRecord
import io.horizontalsystems.bitcoincore.storage.FullTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RecordBitcoinReplacementUseCaseTest {

    private companion object {
        const val ACCOUNT_ID = "account"
        const val BLOCKCHAIN_UID = "bitcoin"
        const val ORIGINAL_HASH = "aaaa"
        const val REPLACEMENT_HASH = "0a0b"
    }

    private var storedLinks = emptySet<String>()
    private val storage = mockk<TransactionSettingsStorage> {
        every { bitcoinReplacementLinks } answers { storedLinks }
        every { bitcoinReplacementLinks = any() } answers { storedLinks = firstArg() }
    }
    private val registry = BitcoinReplacementRegistry(storage)
    private val relink = mockk<RelinkSwapToReplacementUseCase>(relaxed = true)
    private val locallyCreated = mockk<LocallyCreatedTransactionRepository>(relaxed = true)
    private val useCase = RecordBitcoinReplacementUseCase(
        registry,
        relink,
        locallyCreated,
        UnconfinedTestDispatcher().let { TestDispatcherProvider(it, TestScope(it)) },
    )

    private val original = mockk<BitcoinTransactionRecord> {
        every { uid } returns "uid-A"
        every { transactionHash } returns ORIGINAL_HASH
    }
    private val replacement = mockk<FullTransaction> {
        every { header } returns mockk {
            every { uid } returns "uid-B"
            // The kit stores the hash in internal byte order; the reversed hex is what history shows.
            every { hash } returns byteArrayOf(0x0b, 0x0a)
        }
    }

    @Test
    fun invoke_sentReplacement_remembersLinkAndRelinksSwapToReplacement() = runTest {
        useCase(ACCOUNT_ID, BLOCKCHAIN_UID, original, replacement)

        assertEquals(
            listOf(BitcoinReplacementLink(ORIGINAL_HASH, "uid-A", REPLACEMENT_HASH, "uid-B")),
            registry.chainOf(ACCOUNT_ID, BLOCKCHAIN_UID, REPLACEMENT_HASH)?.links,
        )
        coVerify {
            relink.relink(listOf("uid-A", "uid-B"), listOf(ORIGINAL_HASH, REPLACEMENT_HASH), "uid-B", REPLACEMENT_HASH)
        }
    }

    @Test
    fun invoke_sentReplacement_marksReplacementCreatedByWallet() = runTest {
        useCase(ACCOUNT_ID, BLOCKCHAIN_UID, original, replacement)

        coVerify { locallyCreated.markCreated(ACCOUNT_ID, BLOCKCHAIN_UID, REPLACEMENT_HASH, any()) }
    }

    @Test
    fun invoke_relinkFails_keepsLinkAndDoesNotThrow() = runTest {
        coEvery { relink.relink(any(), any(), any(), any()) } throws IllegalStateException("db closed")

        useCase(ACCOUNT_ID, BLOCKCHAIN_UID, original, replacement)

        assertNotNull(registry.chainOf(ACCOUNT_ID, BLOCKCHAIN_UID, REPLACEMENT_HASH))
    }
}
