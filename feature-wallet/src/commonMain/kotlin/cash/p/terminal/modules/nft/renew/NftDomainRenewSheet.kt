package cash.p.terminal.modules.nft.renew

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.core.nft.NftDomainExpiry
import cash.p.terminal.feature.wallet.rememberTransferAction
import cash.p.terminal.modules.nft.operation.NftConfirmSheetContent
import cash.p.terminal.modules.nft.operation.NftOperationOutcome
import cash.p.terminal.modules.nft.operation.NftOperationSheetHost
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.ResultLauncher
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.resources.FeeSettings_NetworkFee
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_refresh_20
import cash.p.terminal.resources.nft_domain_renew_body
import cash.p.terminal.resources.nft_domain_renew_body_many
import cash.p.terminal.resources.nft_domain_renew_confirm
import cash.p.terminal.resources.nft_domain_renew_cost
import cash.p.terminal.resources.nft_domain_renew_partial
import cash.p.terminal.resources.nft_domain_renew_title
import cash.p.terminal.resources.nft_domain_renew_title_many
import cash.p.terminal.resources.nft_domain_renewed
import cash.p.terminal.resources.nft_domains_renewed
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.HudMessenger
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Confirms the renewal of [domains] (not on sale, one or many). Closes itself with a [Result]
 * once the renewal ended; open it through [rememberDomainRenewal], which gates it behind the transfer PIN.
 */
class NftDomainRenewSheet(
    private val domains: List<NftDomainExpiry>,
    private val namesRevealed: Boolean,
) : HSBottomSheet() {

    /** [outcome] is [NftOperationOutcome.Done] or [NftOperationOutcome.Ended]. */
    class Result(val outcome: NftOperationOutcome, val renewUntil: String)

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<NftDomainRenewViewModel> { parametersOf(domains, namesRevealed) }
        val uiState = viewModel.uiState
        NftOperationSheetHost(
            sheet = this,
            navigation = navigation,
            outcome = uiState.outcome,
            resultOf = { Result(it, uiState.renewUntil) },
        ) {
            NftDomainRenewSheetContent(
                uiState = uiState,
                onRenew = viewModel::onRenew,
                onCancel = navigation::navigateUpSafely,
            )
        }
    }
}

/**
 * Returns the action that renews the given domains: the transfer PIN first (a PIN page opened over
 * the sheet would pop it), then the confirmation sheet, then the outcome HUD and [onFinish].
 * [namesRevealed]: the caller shows the names although the balance is hidden.
 */
@Composable
fun HSNavigation.rememberDomainRenewal(
    namesRevealed: Boolean = false,
    onFinish: () -> Unit = {},
): (List<NftDomainExpiry>) -> Unit {
    val revealed by rememberUpdatedState(namesRevealed)
    val hud = rememberHudMessenger()
    val scope = rememberCoroutineScope()
    val launcher: ResultLauncher<Unit> = rememberResultLauncher<NftDomainRenewSheet.Result> { result ->
        scope.launch { hud.showRenewResult(result) }
        onFinish()
    }
    return rememberTransferAction { domains -> launcher.slideFromBottom(NftDomainRenewSheet(domains, revealed)) }
}

private suspend fun HudMessenger.showRenewResult(result: NftDomainRenewSheet.Result) {
    when (val outcome = result.outcome) {
        is NftOperationOutcome.Done -> showSuccess(renewedText(outcome.sent, result.renewUntil))
        is NftOperationOutcome.Ended -> showError(endedText(outcome))
        else -> Unit
    }
}

private suspend fun renewedText(sent: Int, renewUntil: String): String =
    if (sent == 1) {
        getString(Res.string.nft_domain_renewed, renewUntil)
    } else {
        getPluralString(Res.plurals.nft_domains_renewed, sent, sent, renewUntil)
    }

private suspend fun endedText(outcome: NftOperationOutcome.Ended): String {
    val caution = outcome.caution.s.toString()
    if (outcome.sent == 0) return caution
    return getString(Res.string.nft_domain_renew_partial, outcome.sent, outcome.total) + "\n" + caution
}

@Composable
internal fun NftDomainRenewSheetContent(
    uiState: NftDomainRenewUiState,
    onRenew: () -> Unit,
    onCancel: () -> Unit,
) {
    val names = uiState.domainNames
    NftConfirmSheetContent(
        icon = Res.drawable.ic_refresh_20,
        title = titleText(names),
        body = bodyText(names, uiState.renewUntil),
        rows = listOf(
            stringResource(Res.string.nft_domain_renew_cost) to uiState.cost,
            stringResource(Res.string.FeeSettings_NetworkFee) to uiState.fee,
        ),
        error = uiState.insufficientBalance?.getString(),
        confirmTitle = stringResource(Res.string.nft_domain_renew_confirm),
        confirmEnabled = uiState.canRenew,
        inProgress = uiState.outcome == NftOperationOutcome.Running,
        onConfirm = onRenew,
        onCancel = onCancel,
    )
}

@Composable
private fun titleText(names: List<String>): String =
    if (names.size == 1) {
        stringResource(Res.string.nft_domain_renew_title, names.first())
    } else {
        pluralStringResource(Res.plurals.nft_domain_renew_title_many, names.size, names.size)
    }

@Composable
private fun bodyText(names: List<String>, renewUntil: String): String =
    if (names.size == 1) {
        stringResource(Res.string.nft_domain_renew_body, renewUntil)
    } else {
        stringResource(Res.string.nft_domain_renew_body_many, names.joinToString(", "), renewUntil)
    }

private val previewState = NftDomainRenewUiState(
    domainNames = listOf("piratecash.ton"),
    renewUntil = "14 March 2027",
    cost = "0.02 TON",
    fee = "≈ 0.03 TON · $0.15",
    insufficientBalance = null,
    canRenew = true,
    outcome = NftOperationOutcome.Idle,
)

@Composable
private fun PreviewContent(uiState: NftDomainRenewUiState) {
    ComposeAppTheme {
        NftDomainRenewSheetContent(uiState = uiState, onRenew = {}, onCancel = {})
    }
}

@Preview
@Composable
private fun NftDomainRenewSheetSinglePreview() = PreviewContent(previewState)

@Preview
@Composable
private fun NftDomainRenewSheetLoadingFeePreview() = PreviewContent(
    previewState.copy(fee = "---", canRenew = false),
)

@Preview
@Composable
private fun NftDomainRenewSheetBulkPreview() = PreviewContent(
    previewState.copy(
        domainNames = listOf("piratecash.ton", "cosanta.ton", "piratecashbot.ton"),
        cost = "0.06 TON",
        fee = "≈ 0.09 TON · $0.45",
    ),
)

@Preview
@Composable
private fun NftDomainRenewSheetBalanceHiddenPreview() = PreviewContent(
    previewState.copy(domainNames = listOf("****", "****"), cost = "*****", fee = "*****"),
)

@Preview
@Composable
private fun NftDomainRenewSheetInsufficientPreview() = PreviewContent(
    previewState.copy(
        canRenew = false,
        insufficientBalance = TranslatableString.PlainString("Not enough TON: 0.05 TON required, 0.01 TON available."),
    ),
)

@Preview
@Composable
private fun NftDomainRenewSheetRenewingPreview() = PreviewContent(
    previewState.copy(canRenew = false, outcome = NftOperationOutcome.Running),
)
