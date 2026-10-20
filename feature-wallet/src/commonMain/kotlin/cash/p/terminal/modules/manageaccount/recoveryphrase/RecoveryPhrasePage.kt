package cash.p.terminal.modules.manageaccount.recoveryphrase

import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import io.horizontalsystems.core.common.CommonParcelable
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.feature.wallet.FaqTopic
import cash.p.terminal.feature.wallet.openFaq
import cash.p.terminal.modules.manageaccount.safetyrules.SafetyRulesModule
import cash.p.terminal.modules.manageaccount.safetyrules.SafetyRulesPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.modules.manageaccount.ui.ActionButton
import cash.p.terminal.modules.manageaccount.ui.PassphraseCell
import cash.p.terminal.modules.manageaccount.ui.SeedPhraseList
import cash.p.terminal.modules.manageaccount.ui.SeedPhraseQrCard
import cash.p.terminal.resources.Info_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_info_24
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.modules.manageaccount.ui.rememberCopyWithHud
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.AnnotatedResourceString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import kotlinx.coroutines.launch
import io.horizontalsystems.core.common.CommonParcelize
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Alert_Copy
import cash.p.terminal.resources.PrivateKeys_NeverShareWarning
import cash.p.terminal.resources.RecoveryPhrase_QrCodeWarning
import cash.p.terminal.resources.RecoveryPhrase_QrGenerationError
import cash.p.terminal.resources.RecoveryPhrase_Title
import cash.p.terminal.resources.RecoveryPhrase_monero_Title
import cash.p.terminal.resources.private_key_conversion_monero_description

class RecoveryPhrasePage(val input: Input) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        RecoveryPhraseScreen(
            navigation = navigation,
            account = input.account,
            recoveryPhraseType = input.recoveryPhraseType
        )
    }

    @CommonParcelize
    class Input(
        val account: Account,
        val recoveryPhraseType: RecoveryPhraseType
    ) : CommonParcelable

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
        koinViewModel<RecoveryPhraseViewModel> { parametersOf(account, recoveryPhraseType) }

    val copyWithHud = rememberCopyWithHud()
    val hudMessenger = rememberHudMessenger()
    val qrGenerationErrorText = stringResource(Res.string.RecoveryPhrase_QrGenerationError)
    val titleResId = if (recoveryPhraseType == RecoveryPhrasePage.RecoveryPhraseType.Monero) {
        Res.string.RecoveryPhrase_monero_Title
    } else {
        Res.string.RecoveryPhrase_Title
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
                copyWithHud(viewModel.words.joinToString(" "))
            }
        }
    }

    // Show error if QR generation failed
    if (viewModel.qrGenerationError) {
        hudMessenger.showError(qrGenerationErrorText)
        viewModel.onQrGenerationErrorShown()
    }
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(titleResId),
                navigationIcon = {
                    HsBackButton(onClick = navigation::navigateUpSafely)
                },
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Info_Title),
                        icon = Res.drawable.ic_info_24,
                        onClick = {
                            navigation.openFaq(FaqTopic.PrivateKeys)
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
                    text = stringResource(Res.string.PrivateKeys_NeverShareWarning)
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
                                TranslatableString.ResString(
                                    Res.string.private_key_conversion_monero_description,
                                    it,
                                    it
                                ).getString()
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
                    text = stringResource(Res.string.RecoveryPhrase_QrCodeWarning)
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
            ActionButton(Res.string.Alert_Copy) {
                navigateToSafetyRulesForCopy()
            }
        }
    }
}
