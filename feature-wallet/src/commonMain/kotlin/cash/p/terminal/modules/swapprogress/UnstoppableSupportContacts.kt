package cash.p.terminal.modules.swapprogress

import cash.p.terminal.modules.transactionInfo.TransactionInfoViewItem
import cash.p.terminal.network.unstoppable.domain.entity.UnstoppableProviderContacts
import cash.p.terminal.network.unstoppable.domain.repository.UnstoppableRepository
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_support_24
import cash.p.terminal.resources.swap_support_email
import cash.p.terminal.resources.swap_support_telegram
import co.touchlab.kermit.Logger
import io.horizontalsystems.core.Translator
import kotlinx.coroutines.CancellationException

sealed interface SupportContact {
    data class Telegram(val url: String) : SupportContact
    data class Email(val address: String) : SupportContact
}

private const val TELEGRAM_URL = "https://t.me/"

fun UnstoppableProviderContacts?.toSupportContacts(): List<SupportContact> = listOfNotNull(
    this?.telegram?.toTelegramUrl()?.let(SupportContact::Telegram),
    this?.email?.toEmailAddress()?.let(SupportContact::Email),
)

/** Contacts of the provider behind a swap; a failed provider list is logged and yields no contacts. */
suspend fun UnstoppableRepository.supportContacts(subProviderId: String?): List<SupportContact> = try {
    getProviders()
        .firstOrNull { it.provider.equals(subProviderId, ignoreCase = true) }
        ?.contacts
        .toSupportContacts()
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Logger.withTag("SupportContacts").w(e) { "Failed to load provider contacts" }
    emptyList()
}

private fun String.toTelegramUrl(): String? {
    val handle = trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("t.me/")
        .trimStart('@')
    return handle.takeIf { it.isNotBlank() && '/' !in it && ':' !in it }?.let { TELEGRAM_URL + it }
}

// Some providers put a URL into the email field.
private fun String.toEmailAddress(): String? =
    trim().takeIf { '@' in it && "://" !in it }

fun supportSectionItems(
    providerTitle: String,
    contacts: List<SupportContact>,
    emailBody: String,
): List<TransactionInfoViewItem> = contacts.map { contact ->
    when (contact) {
        is SupportContact.Telegram -> TransactionInfoViewItem.Explorer(
            Translator.getString(Res.string.swap_support_telegram, providerTitle),
            contact.url,
            Res.drawable.ic_support_24,
        )

        is SupportContact.Email -> TransactionInfoViewItem.SupportEmail(
            Translator.getString(Res.string.swap_support_email, providerTitle),
            contact.address,
            emailBody,
        )
    }
}
