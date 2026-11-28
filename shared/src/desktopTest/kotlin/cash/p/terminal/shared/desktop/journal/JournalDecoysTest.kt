package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.vault.JournalInboxCrypto
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JournalDecoysTest {

    @Test
    fun overwriteEntries_everyEntry_startsWithACurvePointAsARealSealDoes() = journalScenario { fixture ->
        fixture.levels.create()
        val mailbox = fixture.vault.mailbox

        mailbox.overwriteEntries()

        MailboxRing.entries.forEach { ring ->
            val entries = mutableListOf<ByteArray>()
            mailbox.read(ring) { entries += it; null }
            assertEquals(JournalMailbox.RING_SIZE, entries.size)
            // X25519 public keys are encoded with the top bit clear; random bytes have it set half the time.
            assertTrue(entries.all { it[JournalInboxCrypto.KEY_SIZE - 1].toInt() and HIGH_BIT == 0 }, "$ring")
        }
    }

    @Test
    fun encodeSelfie_imageIoDiskCacheUnusable_encodesInMemory() {
        val cacheDirectory = createTempDirectory("imageio").toFile()
        ImageIO.setUseCache(true)
        ImageIO.setCacheDirectory(cacheDirectory)
        cacheDirectory.delete()
        try {
            assertNotNull(encodeSelfie(BufferedImage(FRAME_WIDTH, FRAME_HEIGHT, BufferedImage.TYPE_INT_RGB)))
        } finally {
            ImageIO.setCacheDirectory(null)
        }
    }

    private companion object {
        const val HIGH_BIT = 0x80
        const val FRAME_WIDTH = 640
        const val FRAME_HEIGHT = 480
    }
}
