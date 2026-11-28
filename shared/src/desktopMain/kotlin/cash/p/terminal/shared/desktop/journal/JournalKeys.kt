package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.core.storage.desktop.VaultSafeDao
import cash.p.terminal.vault.JournalInboxCrypto
import kotlinx.coroutines.runBlocking

/**
 * The journal keys of the safes. Each private key stays in its own safe, the one for a duress level's entries in its
 * parent: a safe never holds what opens, or proves, anything above it.
 */
internal object JournalKeys {

    /** The header must carry the public half of the safe's key, or its failed attempts would be sealed to nobody. */
    fun ensureInboxKey(safe: VaultSafeDao, slot: Int, mailbox: JournalMailbox) {
        val privateKey = safe.bytes(JournalSetting.INBOX_KEY)
            ?: JournalInboxCrypto.generateKeyPair().privateKey.also { safe.putBytes(JournalSetting.INBOX_KEY, it) }
        val publicKey = JournalInboxCrypto.publicKey(privateKey)
        privateKey.fill(0)
        runBlocking { if (!mailbox.publicKey(slot).contentEquals(publicKey)) mailbox.setPublicKey(slot, publicKey) }
    }

    /** A random key nobody holds: from outside it looks the same as the one a parent links. */
    fun putRootNotifyKey(safe: VaultSafeDao) =
        safe.putBytes(JournalSetting.NOTIFY_KEY, JournalInboxCrypto.decoyPublicKey())

    /** [chain] runs from the session root down; each safe below the root is the child of the one before it. */
    fun ensureNotifyKeys(chain: List<VaultSafeDao>, mailbox: JournalMailbox) {
        chain.firstOrNull()?.takeIf { it.bytes(JournalSetting.NOTIFY_KEY) == null }?.let(::putRootNotifyKey)
        chain.zipWithNext { parent, child -> linkNotifyKey(parent, child, mailbox) }
    }

    /** What the parent chose for its duress before the child existed applies now. */
    fun onDuressChildCreated(parent: VaultSafeDao, child: VaultSafeDao, mailbox: JournalMailbox) {
        linkNotifyKey(parent, child, mailbox)
        parent.putLong(JournalSetting.NOTIFY_WATERMARK, lastSeq(mailbox))
        parent.applyDuressFlagsTo(child)
    }

    private fun linkNotifyKey(parent: VaultSafeDao, child: VaultSafeDao, mailbox: JournalMailbox) {
        val privateKey = notifyPrivateKey(parent, mailbox)
        val publicKey = JournalInboxCrypto.publicKey(privateKey)
        privateKey.fill(0)
        if (!child.bytes(JournalSetting.NOTIFY_KEY).contentEquals(publicKey)) {
            child.putBytes(JournalSetting.NOTIFY_KEY, publicKey)
        }
    }

    private fun notifyPrivateKey(parent: VaultSafeDao, mailbox: JournalMailbox): ByteArray =
        parent.bytes(JournalSetting.CHILD_NOTIFY_PRIVATE) ?: JournalInboxCrypto.generateKeyPair().privateKey.also {
            parent.putLong(JournalSetting.NOTIFY_WATERMARK, lastSeq(mailbox))
            parent.putBytes(JournalSetting.CHILD_NOTIFY_PRIVATE, it)
        }

    private fun lastSeq(mailbox: JournalMailbox): Long = runBlocking { mailbox.nextSeq(MailboxRing.NOTIFY) } - 1
}
