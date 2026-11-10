package cash.p.terminal.modules.nft.renew

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.nft.NftDomainExpiry
import cash.p.terminal.feature.wallet.rememberTransferAction
import cash.p.terminal.modules.nft.nftCard
import cash.p.terminal.navigation.BottomSheetDismissHandler
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.ResultLauncher
import cash.p.terminal.navigation.navigateUpFrom
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.resources.FeeSettings_NetworkFee
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_refresh_20
import cash.p.terminal.resources.nft_domain_renew_body
import cash.p.terminal.resources.nft_domain_renew_body_many
import cash.p.terminal.resources.nft_domain_renew_cancel
import cash.p.terminal.resources.nft_domain_renew_confirm
import cash.p.terminal.resources.nft_domain_renew_cost
import cash.p.terminal.resources.nft_domain_renew_partial
import cash.p.terminal.resources.nft_domain_renew_title
import cash.p.terminal.resources.nft_domain_renew_title_many
import cash.p.terminal.resources.nft_domain_renewed
import cash.p.terminal.resources.nft_domains_renewed
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.BottomSheetSurface
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HudMessenger
import cash.p.terminal.ui_compose.components.SectionUniversalItem
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.TitleAndValueCell
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.headline1_leah
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
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

    /** [outcome] is [RenewOutcome.Done] or [RenewOutcome.Ended]. */
    class Result(val outcome: RenewOutcome, val renewUntil: String)

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<NftDomainRenewViewModel> { parametersOf(domains, namesRevealed) }
        val uiState = viewModel.uiState
        val renewing = uiState.outcome == RenewOutcome.Renewing
        var dismissedWhileRenewing by rememberSaveable { mutableStateOf(false) }

        // The send runs in this sheet's view model, so a swipe must not drop it midway.
        // Material has already hidden the sheet by then, so an Idle return must still close it.
        BottomSheetDismissHandler {
            if (renewing) dismissedWhileRenewing = true else navigation.navigateUpSafely()
        }
        LaunchedEffect(uiState.outcome) {
            when (val outcome = uiState.outcome) {
                is RenewOutcome.Done, is RenewOutcome.Ended -> {
                    navigation.setResult(this@NftDomainRenewSheet, Result(outcome, uiState.renewUntil))
                    navigation.navigateUpFrom(this@NftDomainRenewSheet)
                }
                RenewOutcome.Idle -> if (dismissedWhileRenewing) navigation.navigateUpFrom(this@NftDomainRenewSheet)
                RenewOutcome.Renewing -> Unit
            }
        }
        NftDomainRenewSheetContent(
            uiState = uiState,
            onRenew = viewModel::onRenew,
            onCancel = navigation::navigateUpSafely,
        )
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
        is RenewOutcome.Done -> showSuccess(renewedText(outcome.sent, result.renewUntil))
        is RenewOutcome.Ended -> showError(endedText(outcome))
        else -> Unit
    }
}

private suspend fun renewedText(sent: Int, renewUntil: String): String =
    if (sent == 1) {
        getString(Res.string.nft_domain_renewed, renewUntil)
    } else {
        getPluralString(Res.plurals.nft_domains_renewed, sent, sent, renewUntil)
    }

private suspend fun endedText(outcome: RenewOutcome.Ended): String {
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
    val renewing = uiState.outcome == RenewOutcome.Renewing
    BottomSheetSurface {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(uiState)
            Summary(uiState)
            uiState.insufficientBalance?.let { TextImportantError(text = it.getString()) }
            ButtonPrimaryYellow(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.nft_domain_renew_confirm),
                enabled = uiState.canRenew,
                loadingIndicator = renewing,
                onClick = onRenew,
            )
            ButtonPrimaryTransparent(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(Res.string.nft_domain_renew_cancel),
                enabled = !renewing,
                onClick = onCancel,
            )
        }
    }
}

@Composable
private fun Header(uiState: NftDomainRenewUiState) {
    val names = uiState.domainNames
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(ComposeAppTheme.colors.brandDefault15),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.ic_refresh_20),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                colorFilter = ColorFilter.tint(ComposeAppTheme.colors.brandDefault),
            )
        }
        headline1_leah(text = titleText(names), textAlign = TextAlign.Center)
        body_grey(text = bodyText(names, uiState.renewUntil), textAlign = TextAlign.Center)
    }
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

@Composable
private fun Summary(uiState: NftDomainRenewUiState) {
    val rows = listOf(
        stringResource(Res.string.nft_domain_renew_cost) to uiState.cost,
        stringResource(Res.string.FeeSettings_NetworkFee) to uiState.fee,
    )
    Column(Modifier.nftCard()) {
        rows.forEachIndexed { index, (title, value) ->
            SectionUniversalItem(borderTop = index != 0) {
                TitleAndValueCell(title = title, value = value, minHeight = 48.dp)
            }
        }
    }
}

private val previewState = NftDomainRenewUiState(
    domainNames = listOf("piratecash.ton"),
    renewUntil = "14 March 2027",
    cost = "0.02 TON",
    fee = "≈ 0.03 TON · $0.15",
    insufficientBalance = null,
    canRenew = true,
    outcome = RenewOutcome.Idle,
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
    previewState.copy(canRenew = false, outcome = RenewOutcome.Renewing),
)
