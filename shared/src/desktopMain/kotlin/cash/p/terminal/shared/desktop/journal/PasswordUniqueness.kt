package cash.p.terminal.shared.desktop.journal

import cash.p.terminal.core.storage.desktop.LevelSetting
import cash.p.terminal.core.storage.desktop.VaultSafeDao
import cash.p.terminal.shared.desktop.vault.OpenSafe
import cash.p.terminal.vault.PasswordVault
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The journal password is a barrier inside the app, kept as a verifier in the level's safe. It must never equal a
 * vault password, or that verifier would let a level test guesses at a password of the vault.
 */
internal object JournalPassword {
    private const val SALT_SIZE = 16
    private val random = SecureRandom()

    fun isSet(safe: VaultSafeDao): Boolean = safe.setting(JournalSetting.PASSWORD) != null

    fun set(safe: VaultSafeDao, password: CharArray, vault: PasswordVault) {
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        safe.putBytes(JournalSetting.PASSWORD, salt + vault.passwordVerifier(password, salt))
    }

    fun clear(safe: VaultSafeDao) = safe.deleteSetting(JournalSetting.PASSWORD)

    /** One Argon2 run at the vault's cost. */
    fun matches(safe: VaultSafeDao, password: CharArray, vault: PasswordVault): Boolean {
        val stored = safe.bytes(JournalSetting.PASSWORD) ?: return false
        val salt = stored.copyOfRange(0, SALT_SIZE)
        return MessageDigest.isEqual(vault.passwordVerifier(password, salt), stored.copyOfRange(SALT_SIZE, stored.size))
    }
}

/**
 * The copies of the delete-contacts tags a level published for its own slot. Each verifies only the password it was
 * made from, so a copy left behind by a failed step still counts as that password being taken.
 */
internal object EraseTagCopies {
    private const val SEPARATOR = ","

    fun read(safe: VaultSafeDao): List<ByteArray> =
        safe.setting(JournalSetting.ERASE_TAG)?.split(SEPARATOR)?.map(String::hexToByteArray).orEmpty()

    fun write(safe: VaultSafeDao, tags: List<ByteArray>) =
        safe.putSetting(LevelSetting(JournalSetting.ERASE_TAG, tags.joinToString(SEPARATOR) { it.toHexString() }))

    fun clear(safe: VaultSafeDao) = safe.deleteSetting(JournalSetting.ERASE_TAG)

    /** One Argon2 run, and only for a level that holds copies. */
    fun matches(safe: OpenSafe, password: CharArray, vault: PasswordVault): Boolean {
        val copies = read(safe.dao).ifEmpty { return false }
        val tag = vault.eraseTag(password, safe.link.slot)
        return copies.fold(false) { matched, copy -> MessageDigest.isEqual(copy, tag) or matched }
    }
}

/**
 * The check every password setter adds to its own envelope rule: no password may equal one a verifier in a safe of
 * the chain answers to, active erase tag or not.
 */
internal object PasswordUniqueness {
    /** [eraseOwner]'s copies are left out: they are of its own delete-contacts password, the one being set. */
    fun matchesStoredVerifier(
        password: CharArray,
        safes: List<OpenSafe>,
        vault: PasswordVault,
        eraseOwner: OpenSafe? = null,
    ): Boolean = safes.any {
        JournalPassword.matches(it.dao, password, vault) ||
            (it !== eraseOwner && EraseTagCopies.matches(it, password, vault))
    }
}
