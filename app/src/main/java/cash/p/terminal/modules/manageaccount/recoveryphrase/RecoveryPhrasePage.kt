package cash.p.terminal.modules.manageaccount.recoveryphrase

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.core.managers.FaqManager
import cash.p.terminal.modules.manageaccount.safetyrules.SafetyRulesModule
import cash.p.terminal.modules.manageaccount.safetyrules.SafetyRulesPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.modules.manageaccount.ui.ActionButton
import cash.p.terminal.modules.manageaccount.ui.PassphraseCell
import cash.p.terminal.modules.manageaccount.ui.SeedPhraseList
import cash.p.terminal.modules.manageaccount.ui.SeedPhraseQrCard
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.helpers.TextHelper
import cash.p.terminal.ui_compose.AnnotatedResourceString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HudHelper
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import cash.p.terminal.navigation.navigateUpSafely

class RecoveryPhrasePage(val input: Input) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        RecoveryPhraseScreen(
            navigation = navigation,
            account = input.account,
            recoveryPhraseType = input.recoveryPhraseType
        )
    }

    @Parcelize
    class Input(
        val account: Account,
        val recoveryPhraseType: RecoveryPhraseType
    ) : Parcelable

    enum class RecoveryPhraseType {
        Mnemonic,
        Monero // Regular mnemonic with conversion to Monero WORDS
    }
}

@Composable
private fun RecoveryPhraseScreen(
    navigation: HSNavigation,
    account: Account,
    recoveryPhraseType: RecoveryPhrasePage.RecoveryPhraseType
) {
    val viewModel =
        viewModel<RecoveryPhraseViewModel>(
            factory = RecoveryPhraseModule.Factory(
                account,
                recoveryPhraseType
            )
        )

    val view = LocalView.current
    val titleResId = if (recoveryPhraseType == RecoveryPhrasePage.RecoveryPhraseType.Monero) {
        R.string.RecoveryPhrase_monero_Title
    } else {
        R.string.RecoveryPhrase_Title
    }

    // Track hidden state for phrase and QR
    var phraseHidden by remember { mutableStateOf(true) }
    var qrHidden by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Function to navigate to safety rules and handle result for reveal
    fun navigateToSafetyRulesForReveal(onAgreed: () -> Unit) {
        navigation.slideFromBottomForResult<SafetyRulesPage.Result>(
            SafetyRulesPage(SafetyRulesModule.Input(SafetyRulesModule.SafetyRulesMode.AGREE))
        ) { result ->
            if (result == SafetyRulesPage.Result.AGREED) {
                onAgreed()
            }
        }
    }

    // Function to navigate to safety rules for copy confirmation
    fun navigateToSafetyRulesForCopy() {
        navigation.slideFromBottomForResult<SafetyRulesPage.Result>(
            SafetyRulesPage(SafetyRulesModule.Input(SafetyRulesModule.SafetyRulesMode.COPY_CONFIRM))
        ) { result ->
            if (result == SafetyRulesPage.Result.RISK_IT) {
                TextHelper.copyText(viewModel.words.joinToString(" "))
                HudHelper.showSuccessMessage(view, R.string.Hud_Text_Copied)
            }
        }
    }

    // Show error if QR generation failed
    if (viewModel.qrGenerationError) {
        HudHelper.showErrorMessage(view, R.string.RecoveryPhrase_QrGenerationError)
        viewModel.onQrGenerationErrorShown()
    }
    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(titleResId),
                navigationIcon = {
                    HsBackButton(onClick = navigation::navigateUpSafely)
                },
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(R.string.Info_Title),
                        icon = R.drawable.ic_info_24,
                        onClick = {
                            FaqManager.showFaqPage(FaqManager.faqPrivateKeys)
                        }
                    )
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
            ) {
                VSpacer(12.dp)
                TextImportantWarning(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(R.string.PrivateKeys_NeverShareWarning)
                )
                if (recoveryPhraseType == RecoveryPhrasePage.RecoveryPhraseType.Monero) {
                    (account.type as? AccountType.Mnemonic)?.words?.size?.let {
                        TextImportantWarning(
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp
                            ),
                            text = AnnotatedResourceString.htmlToAnnotatedString(
                                stringResource(
                                    R.string.private_key_conversion_monero_description,
                                    it,
                                    it
                                )
                            )
                        )
                    }
                }
                VSpacer(24.dp)
                SeedPhraseList(viewModel.wordsNumbered, phraseHidden) {
                    if (!phraseHidden) {
                        // Already revealed - just hide
                        phraseHidden = true
                    } else if (viewModel.safetyRulesAgreed) {
                        // Already agreed to safety rules - just reveal
                        phraseHidden = false
                    } else {
                        // Need to show safety rules first
                        navigateToSafetyRulesForReveal { phraseHidden = false }
                    }
                }
                viewModel.passphrase?.let { passphrase ->
                    VSpacer(24.dp)
                    PassphraseCell(passphrase, phraseHidden)
                }
                VSpacer(16.dp)
                TextImportantWarning(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(R.string.RecoveryPhrase_QrCodeWarning)
                )
                VSpacer(12.dp)
                SeedPhraseQrCard(
                    encryptedContent = viewModel.encryptedSeedQrContent,
                    hidden = qrHidden,
                    onClick = {
                        if (!qrHidden) {
                            // Already revealed - hide and regenerate
                            viewModel.regenerateEncryptedQrContent()
                            qrHidden = true
                        } else if (viewModel.safetyRulesAgreed) {
                            // Already agreed to safety rules - just reveal
                            qrHidden = false
                            coroutineScope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                        } else {
                            // Need to show safety rules first
                            navigateToSafetyRulesForReveal {
                                qrHidden = false
                                coroutineScope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
                            }
                        }
                    }
                )
                VSpacer(24.dp)
            }
            ActionButton(R.string.Alert_Copy) {
                navigateToSafetyRulesForCopy()
            }
        }
    }
}
