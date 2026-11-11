package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.storage.desktop.LevelSetting
import cash.p.terminal.premium.data.LevelSettings
import co.touchlab.kermit.Logger

private val logger = Logger.withTag("DesktopVaultSettings")

/** Sets whose every element belongs to one account: it lives in that account's store and moves with it. */
class DesktopAccountSettings(private val vault: DesktopVault) {

    fun strings(name: String): Set<String> {
        val session = vault.openSession() ?: return emptySet()
        return session.accountIds.flatMap { accountId ->
            session.account(accountId)?.setting(name)?.split(SEPARATOR)?.filter(String::isNotEmpty).orEmpty()
        }.toSet()
    }

    fun setStrings(name: String, value: Set<String>, ownerOf: (String) -> String) {
        val session = vault.openSession() ?: return logDropped()
        val byOwner = value.groupBy(ownerOf)
        session.accountIds.forEach { accountId ->
            session.account(accountId)?.putSetting(name, byOwner[accountId].orEmpty().joinToString(SEPARATOR))
        }
    }

    private companion object {
        const val SEPARATOR = ","
    }
}

class DesktopLevelSettings(private val vault: DesktopVault) : LevelSettings {
    override fun flag(level: Int, key: String): Boolean? =
        vault.openSession()?.safeOrNull(level)?.setting(key)?.toBooleanStrictOrNull()

    override fun setFlag(level: Int, key: String, value: Boolean) {
        val safe = vault.openSession()?.safeOrNull(level) ?: return logDropped()
        safe.putSetting(LevelSetting(key, value.toString()))
    }
}

/** A screen answering after its session or level went away: the choice belonged to what no longer shows. */
private fun logDropped() = logger.w { "Setting dropped: its storage is not open" }
