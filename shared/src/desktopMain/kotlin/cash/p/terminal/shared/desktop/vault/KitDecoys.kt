package cash.p.terminal.shared.desktop.vault

import java.io.File
import java.security.SecureRandom
import java.util.Random

/** Directories no key opens, shaped like an account's or Monero's, so counting directories does not count accounts. */
internal class KitDecoys(
    private val files: DesktopVaultFiles,
    private val slots: VaultSlots,
    private val random: Random = SecureRandom(),
) {

    fun create() {
        repeat(MIN_DIRECTORIES + random.nextInt(MAX_DIRECTORIES - MIN_DIRECTORIES + 1)) {
            shapes[random.nextInt(shapes.size)](subdirectory(files.kitsDirectory))
        }
    }

    private val shapes: List<(File) -> Unit> = listOf(::accountStore, ::moneroWallets)

    /** A real account has a directory per database kit, and only one each for Zcash, Beam and MWEB. */
    private val kitShapes: List<(File) -> Unit> =
        List<(File) -> Unit>(MAX_KIT_DIRECTORIES - 3) { ::database } +
            listOf(::zcashDatabase, ::beamWallet, ::mwebDaemon)

    private fun accountStore(directory: File) {
        val name = randomName()
        noise(File(directory, name))
        File(directory, "$name.lck").createNewFile()
        kitShapes.shuffled(random).take(random.nextInt(kitShapes.size + 1)).forEach { it(subdirectory(directory)) }
    }

    private fun database(directory: File) {
        repeat(slots.pagesBetween(1, MAX_KIT_DATABASES)) {
            val name = randomName()
            noise(File(directory, name))
            File(directory, "$name.lck").createNewFile()
        }
        File(directory, ".${randomName()}-sqlcipher.lock").createNewFile()
    }

    /** Opened by the SDK's own SQLite: no Room lock and no namespace lock, just its database and the pristine mark. */
    private fun zcashDatabase(directory: File) {
        noise(File(directory, randomName()))
        if (random.nextBoolean()) File(directory, randomName()).createNewFile()
    }

    private fun beamWallet(directory: File) = noise(File(directory, "wallet.db"))

    /** Without Litecoin the MWEB directory stays empty, and that is what most real accounts have. */
    private fun mwebDaemon(directory: File) {
        if (random.nextBoolean()) return
        val daemon = subdirectory(directory)
        listOf("neutrino.db", "peers.json", "block_headers.bin", "reg_filter_headers.bin")
            .forEach { noise(File(daemon, it)) }
    }

    private fun moneroWallets(directory: File) = repeat(1 + random.nextInt(MAX_MONERO_WALLETS)) {
        // As GenerateMoneroWalletUseCase names a wallet, at some moment before now.
        val name = "wallet_${System.currentTimeMillis() - (random.nextDouble() * MONERO_MAX_AGE_MS).toLong()}"
        noise(File(directory, name))
        noise(File(directory, "$name.keys"))
    }

    private fun subdirectory(parent: File) = File(parent, randomName()).apply { mkdirs() }

    private fun noise(file: File) = slots.writeNoise(file, slots.pagesBetween(MIN_FILE_PAGES, MAX_FILE_PAGES))

    private fun randomName() = ByteArray(NAME_BYTES).also(random::nextBytes).toHexString()

    companion object {
        /** One each for Ethereum, TON, Tron, Solana, Stellar, Thorchain, Bitcoin, MWEB, Zcash and Beam. */
        const val MAX_KIT_DIRECTORIES = 10

        /** A real directory holds a pair per Monero wallet; more wallets than this stand out from the decoys. */
        const val MAX_MONERO_WALLETS = 4
        private const val MIN_DIRECTORIES = 8
        private const val MAX_DIRECTORIES = 20
        private const val MAX_KIT_DATABASES = 48
        private const val MIN_FILE_PAGES = 1
        private const val MAX_FILE_PAGES = 256
        private const val NAME_BYTES = 16
        private const val MONERO_MAX_AGE_MS = 365L * 24 * 60 * 60 * 1000
    }
}
