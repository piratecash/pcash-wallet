package cash.p.terminal.modules.manageaccounts

import cash.p.terminal.modules.main.MainPage
import io.horizontalsystems.hdwalletkit.Language
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ManageAccountsModuleTest {

    @Test
    fun input_prefillMnemonicLanguageName_returnsLanguage() {
        val input = ManageAccountsModule.Input(
            popOffOnSuccess = MainPage::class,
            popOffInclusive = false,
            prefillMnemonicLanguageName = Language.Japanese.name
        )

        assertEquals(Language.Japanese, input.prefillMnemonicLanguage)
    }

    @Test
    fun input_invalidPrefillMnemonicLanguageName_returnsNullLanguage() {
        val input = ManageAccountsModule.Input(
            popOffOnSuccess = MainPage::class,
            popOffInclusive = false,
            prefillMnemonicLanguageName = "Esperanto"
        )

        assertNull(input.prefillMnemonicLanguage)
    }
}
