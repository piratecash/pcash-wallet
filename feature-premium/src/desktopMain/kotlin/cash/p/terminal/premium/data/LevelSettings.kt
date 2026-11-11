package cash.p.terminal.premium.data

/** Settings stored in the safe of a level, which nothing on any other level can read. */
interface LevelSettings {
    fun flag(level: Int, key: String): Boolean?
    fun setFlag(level: Int, key: String, value: Boolean)
}
