package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.vault.PasswordVault
import java.util.Random
import kotlin.io.path.createTempDirectory
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VaultSlotsNoiseTest {

    @Test
    fun noisePages_seededSource_coversTheWholeRangeEvenlyOnALogScale() {
        val slots = VaultSlots(DesktopVaultFiles(createTempDirectory("noise").toFile()), Random(SEED))

        val pages = List(DRAWS) { slots.noisePages() }

        assertTrue(pages.all { it in MIN_PAGES..MAX_PAGES }, "${pages.min()}..${pages.max()}")
        assertTrue(pages.max() > MAX_PAGES * 9 / 10, "largest draw ${pages.max()}")
        assertTrue(pages.min() <= MIN_PAGES + 1, "smallest draw ${pages.min()}")
        val width = ln(MAX_PAGES.toDouble() / MIN_PAGES) / BUCKETS
        val counts = pages.groupingBy { (ln(it.toDouble() / MIN_PAGES) / width).toInt().coerceAtMost(BUCKETS - 1) }
            .eachCount()
        val expected = DRAWS / BUCKETS
        (0 until BUCKETS).toList().forEach { bucket ->
            val count = counts[bucket] ?: 0
            assertTrue(count in expected * 8 / 10..expected * 12 / 10, "bucket $bucket holds $count of $DRAWS")
        }
    }

    @Test
    fun prepareAll_seededSource_givesBothFilesOfEveryFreeSlotWholePagesInRange() {
        val directory = createTempDirectory("noise").toFile()
        val files = DesktopVaultFiles(directory)
        try {
            VaultSlots(files, Random(SEED)).prepareAll(occupiedSlot = 0)

            val sizes = (1 until PasswordVault.SLOT_COUNT).flatMap { slot ->
                listOf(files.safeFile(slot).length(), files.premiumFile(slot).length())
            }
            assertEquals((PasswordVault.SLOT_COUNT - 1) * 2, sizes.size)
            assertTrue(sizes.all { it % PAGE_SIZE == 0L && it / PAGE_SIZE in MIN_PAGES..MAX_PAGES }, "$sizes")
            assertTrue(sizes.max() > MAX_PAGES * PAGE_SIZE / 16, "$sizes")
        } finally {
            directory.deleteRecursively()
        }
    }

    private companion object {
        const val SEED = 1251L
        const val DRAWS = 20_000
        const val BUCKETS = 6
        const val PAGE_SIZE = 4096L
        const val MIN_PAGES = 8
        const val MAX_PAGES = 4096
    }
}
