package cash.p.terminal.modules.pin

import androidx.compose.runtime.Composable
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.StringResource

class DuressNote(val icon: DrawableResource, val title: StringResource, val description: StringResource)

/** The texts that depend on whether the secret is a PIN or a password. */
class DuressWording(
    val introDescription: StringResource,
    val introNotes: List<DuressNote>,
    val termsWarning: StringResource,
    val termsCheckboxes: StringArrayResource,
)

/** The pages that take the secret itself, which only the platform knows how to ask for. */
interface DuressSecretPages {
    val wording: DuressWording

    /** [accountIds] are the wallets the duress level will show; null when there was nothing to choose. */
    fun setDuress(accountIds: List<String>?): HSPage

    /** The action that leads from the hidden wallet terms to the page where its secret is chosen. */
    @Composable
    fun rememberOpenHiddenWalletSecret(navigation: HSNavigation): () -> Unit
}
