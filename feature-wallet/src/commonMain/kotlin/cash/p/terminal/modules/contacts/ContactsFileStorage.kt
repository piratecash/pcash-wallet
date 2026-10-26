package cash.p.terminal.modules.contacts

interface ContactsFileStorage {
    /** `null` when nothing has been saved yet. */
    fun read(): String?

    fun write(json: String)
}
