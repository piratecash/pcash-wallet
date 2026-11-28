package cash.p.terminal.feature.logging.settings

import kotlinx.coroutines.flow.Flow

/** The journal secret and the delete-contacts secret, as far as the journal settings need them. */
interface LoginJournalSecrets {
    val changes: Flow<Unit>

    fun isJournalSecretSet(): Boolean
    fun isDeleteContactsSecretSet(): Boolean
    fun disableJournalSecret()
    fun disableDeleteContactsSecret()
}
