package cash.p.terminal.modules.pin

enum class LevelOutcome { SUCCESS, PASSWORD_IN_USE, LIMIT_REACHED, FAILED }

/** What the duress and hidden wallet screens ask of the platform's secret (a PIN or a password) store. */
interface DuressSecretController {
    /** Creates the duress level, or changes its secret when it exists; takes [secret] and wipes it. */
    suspend fun setDuressSecret(secret: CharArray): LevelOutcome

    suspend fun disableDuress()

    /** Creates the hidden wallet and opens it; takes [secret] and wipes it. */
    suspend fun createHiddenWallet(secret: CharArray): LevelOutcome

    suspend fun isDuressSet(): Boolean

    /** False inside a hidden wallet, where duress does not exist. */
    suspend fun isDuressAvailable(): Boolean
}
