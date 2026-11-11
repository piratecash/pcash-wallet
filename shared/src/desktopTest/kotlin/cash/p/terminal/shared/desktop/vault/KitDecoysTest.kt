package cash.p.terminal.shared.desktop.vault

import java.io.File
import java.util.Random
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KitDecoysTest {
    private val directory = createTempDirectory("kit-decoys").toFile()
    private val files = DesktopVaultFiles(directory)

    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun create_anySeed_makesEightToTwentyDirectoriesThatHoldFiles() {
        SEEDS.forEach { seed ->
            val decoys = decoysOf(seed)

            assertTrue(decoys.size in 8..20, "seed $seed made ${decoys.size}")
            decoys.forEach { decoy ->
                assertTrue(decoy.isDirectory)
                assertTrue(decoy.walkTopDown().any(File::isFile), "seed $seed: ${decoy.name} is empty")
            }
        }
    }

    @Test
    fun create_anySeed_accountDecoysCoverEveryKitDirectoryCountARealAccountHas() {
        val counts = SEEDS.flatMap { seed ->
            decoysOf(seed).filter(::isAccountDecoy).map { kitDirectoriesOf(it).size }
        }

        assertEquals((0..KitDecoys.MAX_KIT_DIRECTORIES).toSet(), counts.toSet())
    }

    @Test
    fun create_anySeed_kitDirectoriesRepeatWhatOneAccountCanHold() {
        SEEDS.forEach { seed ->
            decoysOf(seed).filter(::isAccountDecoy).forEach { decoy ->
                val kits = kitDirectoriesOf(decoy)
                val names = kits.map { kit -> kit.walkTopDown().filter(File::isFile).map(File::getName).toList() }
                assertTrue(kits.count(::isZcashShaped) <= 1, "seed $seed: two Zcash databases")
                assertTrue(names.count { "wallet.db" in it } <= 1, "seed $seed: two Beam wallets")
                assertTrue(names.count { "neutrino.db" in it } <= 1, "seed $seed: two MWEB daemons")
                assertTrue(names.count { it.isEmpty() } <= 1, "seed $seed: two empty kit directories")
                assertTrue(names.flatten().none { it.endsWith(".keys") }, "seed $seed: a Monero wallet in an account")
            }
        }
    }

    @Test
    fun create_anySeed_someKitDirectoryIsEmptyLikeTheMwebOfABitcoinOnlyAccount() {
        val emptyKits = SEEDS.flatMap { seed ->
            decoysOf(seed).filter(::isAccountDecoy).flatMap(::kitDirectoriesOf).filter { kit ->
                kit.walkTopDown().none(File::isFile)
            }
        }

        assertTrue(emptyKits.isNotEmpty())
    }

    @Test
    fun create_anySeed_namesMoneroWalletsWithARecentCreationTime() {
        val now = System.currentTimeMillis()
        val times = SEEDS.flatMap { seed ->
            decoysOf(seed)
            files.kitsDirectory.walkTopDown().mapNotNull { MONERO_KEYS.matchEntire(it.name)?.groupValues?.get(1) }.toList()
        }.map(String::toLong)

        assertTrue(times.isNotEmpty())
        assertTrue(times.all { it in (now - YEAR_MS)..System.currentTimeMillis() }, "$times")
    }

    @Test
    fun create_anySeed_moneroDecoysHoldVaryingPairsOfWalletAndKeysFiles() {
        val pairCounts = SEEDS.flatMap { seed ->
            decoysOf(seed).filter(::isMoneroDecoy).map { decoy ->
                val names = decoy.listFiles().orEmpty().map(File::getName)
                val keys = names.filter { it.endsWith(".keys") }
                assertEquals(keys.map { it.removeSuffix(".keys") }.toSet(), names.filterNot { it.endsWith(".keys") }.toSet())
                keys.size
            }
        }

        assertTrue(pairCounts.all { it in 1..KitDecoys.MAX_MONERO_WALLETS }, "$pairCounts")
        assertEquals((1..KitDecoys.MAX_MONERO_WALLETS).toSet(), pairCounts.toSet())
    }

    @Test
    fun create_anySeed_someKitDirectoryMatchesAClosedZcashDatabase() {
        val closedZcash = SEEDS.flatMap { seed ->
            decoysOf(seed).filter(::isAccountDecoy).flatMap(::kitDirectoriesOf).filter(::isZcashShaped).map { kit ->
                kit.listFiles().orEmpty().map(File::length)
            }
        }

        assertTrue(closedZcash.isNotEmpty())
        closedZcash.forEach { sizes ->
            assertEquals(1, sizes.count { it > 0 }, "one database")
            assertTrue(sizes.size <= 2, "and at most its pristine mark")
        }
    }

    @Test
    fun create_anySeed_namesOnlyWhatARealDirectoryCouldHold() {
        val names = SEEDS.flatMap { seed ->
            decoysOf(seed)
            files.kitsDirectory.walkTopDown().filter { it != files.kitsDirectory }.map(File::getName).toList()
        }

        assertEquals(emptyList(), names.filterNot { REAL_NAME.matches(it) })
        listOf("wallet.db", "neutrino.db").forEach { assertTrue(it in names, "no decoy repeats $it") }
        assertTrue(names.any { it.endsWith(".keys") }, "no decoy repeats a Monero wallet")
        assertTrue(names.any { it.endsWith("-sqlcipher.lock") }, "no decoy repeats the kits' migration lock")
    }

    @Test
    fun create_vaultCreated_decoysPrecedeAnyAccount() = levelsScenario { fixture ->
        fixture.create()

        assertTrue(fixture.files.kitsDirectory.listFiles().orEmpty().count(File::isDirectory) in 8..20)
    }

    private fun decoysOf(seed: Long): List<File> {
        files.kitsDirectory.deleteRecursively()
        val random = Random(seed)
        KitDecoys(files, VaultSlots(files, random), random).create()
        return files.kitsDirectory.listFiles().orEmpty().toList()
    }

    private fun isAccountDecoy(decoy: File) = decoy.listFiles().orEmpty().any { it.name.endsWith(".lck") }

    private fun isMoneroDecoy(decoy: File) = decoy.listFiles().orEmpty().any { it.name.endsWith(".keys") }

    private fun isZcashShaped(kit: File): Boolean = kit.listFiles().orEmpty().let { children ->
        children.isNotEmpty() && children.none { it.isDirectory || it.name.contains('.') || it.name in SDK_FIXED_FILES }
    }

    private fun kitDirectoriesOf(decoy: File) = decoy.listFiles().orEmpty().filter(File::isDirectory)

    private companion object {
        val SEEDS = 1L..12L
        const val YEAR_MS = 365L * 24 * 60 * 60 * 1000
        val MONERO_KEYS = Regex("wallet_(\\d{13})\\.keys")
        val SDK_FIXED_FILES = setOf("wallet.db")
        val REAL_NAME = Regex(
            "\\.?[0-9a-f]{32}(-sqlcipher\\.lock|\\.lck)?|wallet\\.db|wallet_\\d{13}(\\.keys)?|" +
                "neutrino\\.db|peers\\.json|block_headers\\.bin|reg_filter_headers\\.bin",
        )
    }
}
