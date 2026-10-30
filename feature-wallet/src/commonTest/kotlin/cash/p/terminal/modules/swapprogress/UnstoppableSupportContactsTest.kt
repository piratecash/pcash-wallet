package cash.p.terminal.modules.swapprogress

import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableProviderContacts
import org.junit.Assert.assertEquals
import org.junit.Test

class UnstoppableSupportContactsTest {

    private fun contacts(email: String? = null, telegram: String? = null) =
        UnstoppableProviderContacts(email = email, telegram = telegram, twitter = null, website = null)

    @Test
    fun toSupportContacts_atHandle_buildsTelegramUrl() {
        val result = contacts(telegram = "@pegasusswap_support").toSupportContacts()

        assertEquals(listOf(SupportContact.Telegram("https://t.me/pegasusswap_support")), result)
    }

    @Test
    fun toSupportContacts_bareHandle_buildsTelegramUrl() {
        val result = contacts(telegram = "support").toSupportContacts()

        assertEquals(listOf(SupportContact.Telegram("https://t.me/support")), result)
    }

    @Test
    fun toSupportContacts_existingTelegramUrl_keepsHandle() {
        val result = contacts(telegram = "https://t.me/support").toSupportContacts()

        assertEquals(listOf(SupportContact.Telegram("https://t.me/support")), result)
    }

    @Test
    fun toSupportContacts_telegramAndEmail_telegramFirst() {
        val result = contacts(email = "help@example.com", telegram = "@help").toSupportContacts()

        assertEquals(
            listOf(SupportContact.Telegram("https://t.me/help"), SupportContact.Email("help@example.com")),
            result,
        )
    }

    @Test
    fun toSupportContacts_urlInEmailField_skipped() {
        val result = contacts(email = "https://circle.example/support").toSupportContacts()

        assertEquals(emptyList<SupportContact>(), result)
    }

    @Test
    fun toSupportContacts_emptyOrNullContacts_returnsEmpty() {
        assertEquals(emptyList<SupportContact>(), contacts().toSupportContacts())
        assertEquals(emptyList<SupportContact>(), contacts(email = " ", telegram = "@").toSupportContacts())
        assertEquals(emptyList<SupportContact>(), (null as UnstoppableProviderContacts?).toSupportContacts())
    }
}
