package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.PasswordError

enum class PasswordRequirement {
    MIN_LENGTH,
    UPPERCASE,
    LOWERCASE,
    DIGIT,
    SPECIAL;

    fun isMet(password: String): Boolean = when (this) {
        MIN_LENGTH -> password.length >= MIN_LENGTH_CHARS
        UPPERCASE -> password.contains(UPPERCASE_PATTERN)
        LOWERCASE -> password.contains(LOWERCASE_PATTERN)
        DIGIT -> password.contains(DIGIT_PATTERN)
        SPECIAL -> password.contains(SPECIAL_PATTERN)
    }

    private companion object {
        const val MIN_LENGTH_CHARS = 8
        val UPPERCASE_PATTERN = Regex("[A-Z]")
        val LOWERCASE_PATTERN = Regex("[a-z]")
        val DIGIT_PATTERN = Regex("[0-9]")

        // Deliberately excludes the dot and the space, both of which are allowed characters.
        val SPECIAL_PATTERN = Regex("['\"`&/?!:;,~*\$=+\\-\\[\\](){}<>\\\\_#@|%]")
    }
}

class PassphraseValidator {
    private val allowedCharacters =
        "abcdefghijklmnopqrstuvwxyz0123456789 '\"`&/?!:;.,~*$=+-[](){}<>\\_#@|%"

    fun containsValidCharacters(text: String?): Boolean {
        return text?.all { allowedCharacters.contains(it, ignoreCase = true) } ?: true
    }

    fun unmetRequirements(password: String): List<PasswordRequirement> =
        PasswordRequirement.entries.filterNot { it.isMet(password) }

    @Throws(PasswordError::class)
    fun validatePassword(password: String) {
        if (unmetRequirements(password).isNotEmpty()) {
            throw PasswordError.PasswordInvalid
        }
    }

}
