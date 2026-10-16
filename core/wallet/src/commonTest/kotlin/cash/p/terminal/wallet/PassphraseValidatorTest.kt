package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.PasswordError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

class PassphraseValidatorTest {

    private val validator = PassphraseValidator()

    @Test
    fun unmetRequirements_compliantPassword_isEmpty() {
        assertEquals(emptyList<PasswordRequirement>(), validator.unmetRequirements(VALID))
    }

    @Test
    fun unmetRequirements_sevenCharacters_reportsMinLengthOnly() {
        assertEquals(listOf(PasswordRequirement.MIN_LENGTH), validator.unmetRequirements("Abcde1!"))
    }

    @Test
    fun unmetRequirements_noUppercase_reportsUppercaseOnly() {
        assertEquals(listOf(PasswordRequirement.UPPERCASE), validator.unmetRequirements("abcdefg1!"))
    }

    @Test
    fun unmetRequirements_noLowercase_reportsLowercaseOnly() {
        assertEquals(listOf(PasswordRequirement.LOWERCASE), validator.unmetRequirements("ABCDEFG1!"))
    }

    @Test
    fun unmetRequirements_noDigit_reportsDigitOnly() {
        assertEquals(listOf(PasswordRequirement.DIGIT), validator.unmetRequirements("Abcdefgh!"))
    }

    @Test
    fun unmetRequirements_noSpecial_reportsSpecialOnly() {
        assertEquals(listOf(PasswordRequirement.SPECIAL), validator.unmetRequirements("Abcdefg1"))
    }

    @Test
    fun unmetRequirements_emptyPassword_reportsEveryRequirement() {
        assertEquals(PasswordRequirement.entries.toList(), validator.unmetRequirements(""))
    }

    @Test
    fun unmetRequirements_dotAsOnlyPunctuation_reportsSpecial() {
        assertEquals(listOf(PasswordRequirement.SPECIAL), validator.unmetRequirements("Abcdefg1."))
    }

    @Test
    fun unmetRequirements_spaceAsOnlyPunctuation_reportsSpecial() {
        assertEquals(listOf(PasswordRequirement.SPECIAL), validator.unmetRequirements("Abcdefg1 "))
    }

    @Test
    fun validatePassword_compliantPassword_doesNotThrow() {
        validator.validatePassword(VALID)
    }

    @Test
    fun validatePassword_missingSingleRequirement_throwsPasswordInvalid() {
        assertFailsWith<PasswordError.PasswordInvalid> { validator.validatePassword("Abcdefg1") }
    }

    @Test
    fun validatePassword_dotIsNotSpecial_throwsPasswordInvalid() {
        assertFailsWith<PasswordError.PasswordInvalid> { validator.validatePassword("Abcdefg1.") }
    }

    @Test
    fun containsValidCharacters_latinPassword_isTrue() {
        assertTrue(validator.containsValidCharacters(VALID))
    }

    @Test
    fun containsValidCharacters_cyrillicPassword_isFalse() {
        assertFalse(validator.containsValidCharacters("Пароль1!"))
    }

    @Test
    fun containsValidCharacters_null_isTrue() {
        assertTrue(validator.containsValidCharacters(null))
    }

    private companion object {
        const val VALID = "Abcdefg1!"
    }
}
