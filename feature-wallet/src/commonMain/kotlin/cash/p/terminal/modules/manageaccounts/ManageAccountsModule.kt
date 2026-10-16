package cash.p.terminal.modules.manageaccounts

import cash.p.terminal.core.tryOrNull
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.premium.domain.usecase.PremiumType
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import io.horizontalsystems.hdwalletkit.Language
import kotlin.reflect.KClass
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

object ManageAccountsModule {
    data class Input(
        val popOffOnSuccess: KClass<out HSPage>,
        val popOffInclusive: Boolean,
        val defaultRoute: String? = null,
        val accountId: String? = null,
        // Pre-filled seed phrase data from QR scan
        val prefillWords: List<String>? = null,
        val prefillPassphrase: String? = null,
        val prefillMoneroHeight: Long? = null,
        val prefillMnemonicLanguageName: String? = null
    ) {
        val prefillMnemonicLanguage: Language?
            get() = prefillMnemonicLanguageName?.let { tryOrNull { Language.valueOf(it) } }
    }

    data class AccountViewItem(
        val accountId: String,
        val title: String,
        val subtitle: String,
        val selected: Boolean,
        val backupRequired: Boolean,
        val showAlertIcon: Boolean,
        val isHardwareWallet: Boolean,
        val showNfcIcon: Boolean,
        val migrationRequired: Boolean,
        val premiumType: PremiumType = PremiumType.NONE,
    )

    data class ActionViewItem(
        val icon: DrawableResource,
        val title: StringResource,
        val enabled: Boolean = true,
        val callback: () -> Unit
    )

    @CommonParcelize
    enum class Mode : CommonParcelable {
        Manage, Switcher
    }

}
