package cash.p.terminal.modules.paycore

/** Platform key-value store behind [PayCoreSecureStorage]; values arrive already encrypted. */
interface PayCorePreferences {
    fun getString(key: String): String?

    /** Must be durable on return: the verification flow can be interrupted by process death. */
    fun putStringDurably(key: String, value: String)
}
