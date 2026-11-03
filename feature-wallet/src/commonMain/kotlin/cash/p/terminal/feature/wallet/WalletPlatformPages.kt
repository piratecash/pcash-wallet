package cash.p.terminal.feature.wallet

import androidx.compose.runtime.Composable
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.modules.activatetoken.ActivateTokenPage
import cash.p.terminal.modules.manageaccounts.ManageAccountsModule
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.ui.dialogs.ConfirmationDialogSheet
import org.koin.compose.koinInject
import kotlin.reflect.KClass

/**
 * Pages and hardware-card flows of the wallet screens that only a platform provides.
 * A `null` page hides its entry, so this is the single place that decides availability.
 */
interface WalletPlatformPages {
    fun hardwareWallet(input: ManageAccountsModule.Input?): HSPage?

    /** Hides the QR-scan entries when the platform has no scanner; the page itself comes from AppPages. */
    val isQrScannerAvailable: Boolean

    /** Reports [ResetToFactoryResult]. */
    fun resetToFactory(account: Account): HSPage?
    fun faq(topic: FaqTopic): HSPage?

    /** The app-icon picker; `null` hides its row in Appearance. */
    fun appIcon(): HSPage?

    /** Hides every Blockchain Status entry when `false`. */
    val isBlockchainStatusAvailable: Boolean
    fun blockchainStatus(blockchain: Blockchain): HSPage?

    fun openMarkdownOrWeblink(navigation: HSNavigation, url: String)

    /** Scans the card; its access-code recovery page, or `null` when the scan did not succeed. */
    suspend fun accessCodeRecovery(): HSPage?
    suspend fun changeAccessCode()
    suspend fun forgotAccessCode(cardId: String)

    /** Owns the view models the wallet tabs share with their child pages. */
    val mainPage: KClass<out HSPage>

    /** Reports [ActivateTokenResult]. */
    fun activateToken(wallet: Wallet): HSPage? = ActivateTokenPage(wallet)

    /** A warning sheet; [onConfirm] runs when its action button is tapped. */
    fun confirmation(
        title: String,
        warningText: String,
        actionButtonTitle: String,
        transparentButtonTitle: String,
        onConfirm: () -> Unit,
    ): HSPage? = ConfirmationDialogSheet(
        title = title,
        icon = null,
        warningTitle = null,
        warningText = warningText,
        actionButtonTitle = actionButtonTitle,
        transparentButtonTitle = transparentButtonTitle,
        listener = object : ConfirmationDialogSheet.Listener {
            override fun onActionButtonClick() = onConfirm()
        },
    )
}

data class ActivateTokenResult(val activated: Boolean)

fun HSNavigation.openFaq(topic: FaqTopic) {
    getKoinInstance<WalletPlatformPages>().faq(topic)?.let { slideFromBottom(it) }
}

@Composable
fun qrScannerAvailable(): Boolean = koinInject<WalletPlatformPages>().isQrScannerAvailable

data class ResetToFactoryResult(val success: Boolean)

/** Markdown files read by [LocalizedAssetReader] as `<assetPrefix><language>.md`. */
enum class FaqTopic(val assetPrefix: String) {
    MigrationRequired("faq_migration_required_"),
    MigrationRecommended("faq_migration_recommended_"),
    PrivateKeys("faq_private_keys_"),
}
