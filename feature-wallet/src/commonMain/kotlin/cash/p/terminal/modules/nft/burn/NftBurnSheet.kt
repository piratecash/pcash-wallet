package cash.p.terminal.modules.nft.burn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.core.nft.NftOwner
import cash.p.terminal.core.nft.owner
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.feature.wallet.rememberTransferAction
import cash.p.terminal.modules.nft.operation.NftConfirmSheetContent
import cash.p.terminal.modules.nft.operation.NftConfirmStyle
import cash.p.terminal.modules.nft.operation.NftOperationOutcome
import cash.p.terminal.modules.nft.operation.NftOperationSheetHost
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.navigation.rememberResultLauncher
import cash.p.terminal.resources.FeeSettings_NetworkFee
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_fire_24
import cash.p.terminal.resources.nft_burn_address
import cash.p.terminal.resources.nft_burn_body
import cash.p.terminal.resources.nft_burn_confirm
import cash.p.terminal.resources.nft_burn_domain_body
import cash.p.terminal.resources.nft_burn_domain_title
import cash.p.terminal.resources.nft_burn_sent
import cash.p.terminal.resources.nft_burn_title
import cash.p.terminal.resources.nft_domain_burn_sent
import cash.p.terminal.resources.nft_unnamed
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.DispatcherProvider
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Confirms sending [item] to the burn address; [domain] is set when the item is a TON DNS domain.
 * Closes itself with a [Result]; open it through [rememberNftBurn], which gates it behind the transfer PIN.
 */
class NftBurnSheet(
    private val item: NftItemRecord,
    private val domain: String?,
    private val namesRevealed: Boolean,
) : HSBottomSheet() {

    /** [outcome] is [NftOperationOutcome.Done] or [NftOperationOutcome.Ended]. */
    class Result(val outcome: NftOperationOutcome, val isDomain: Boolean)

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val viewModel = koinViewModel<NftBurnViewModel> { parametersOf(item, domain, namesRevealed) }
        val uiState = viewModel.uiState
        NftOperationSheetHost(
            sheet = this,
            navigation = navigation,
            outcome = uiState.outcome,
            resultOf = { Result(it, uiState.isDomain) },
        ) {
            NftBurnSheetContent(
                uiState = uiState,
                onBurn = viewModel::onBurn,
                onCancel = navigation::navigateUpSafely,
            )
        }
    }
}

/**
 * Returns the action that burns an NFT: the transfer PIN first (a PIN page opened over the sheet would
 * pop it), then the confirmation sheet, then the outcome HUD, a forced refresh of the owner and [onFinish].
 */
@Composable
fun HSNavigation.rememberNftBurn(
    namesRevealed: Boolean,
    onFinish: (NftBurnSheet.Result) -> Unit,
): (item: NftItemRecord, domain: String?) -> Unit {
    val revealed by rememberUpdatedState(namesRevealed)
    val hud = rememberHudMessenger()
    val repository = koinInject<NftCollectionsRepository>()
    val dispatcherProvider = koinInject<DispatcherProvider>()
    // Resolved now: the caller may close right after onFinish and take a composition scope with it.
    val nftBurned = stringResource(Res.string.nft_burn_sent)
    val domainBurned = stringResource(Res.string.nft_domain_burn_sent)
    val launcher = rememberResultLauncher<NftBurnSheet.Result, NftOwner> { result, owner ->
        // The burned row should leave the cache as soon as the network reports it.
        dispatcherProvider.applicationScope.launch { repository.refresh(setOf(owner), force = true) }
        when (val outcome = result.outcome) {
            is NftOperationOutcome.Done -> hud.showSuccess(if (result.isDomain) domainBurned else nftBurned)
            is NftOperationOutcome.Ended -> hud.showError(outcome.caution.s.toString())
            else -> Unit
        }
        onFinish(result)
    }
    val burn = rememberTransferAction<Pair<NftItemRecord, String?>> { (item, domain) ->
        launcher.slideFromBottom(NftBurnSheet(item, domain, revealed), item.owner)
    }
    return { item, domain -> burn(item to domain) }
}

@Composable
internal fun NftBurnSheetContent(
    uiState: NftBurnUiState,
    onBurn: () -> Unit,
    onCancel: () -> Unit,
) {
    val name = uiState.name ?: stringResource(Res.string.nft_unnamed)
    NftConfirmSheetContent(
        icon = Res.drawable.ic_fire_24,
        title = if (uiState.isDomain) {
            stringResource(Res.string.nft_burn_domain_title, name)
        } else {
            stringResource(Res.string.nft_burn_title)
        },
        body = stringResource(
            if (uiState.isDomain) Res.string.nft_burn_domain_body else Res.string.nft_burn_body,
            name,
        ),
        rows = listOf(
            stringResource(Res.string.nft_burn_address) to uiState.burnAddress,
            stringResource(Res.string.FeeSettings_NetworkFee) to uiState.fee,
        ),
        error = uiState.insufficientBalance?.getString(),
        confirmTitle = stringResource(Res.string.nft_burn_confirm),
        confirmStyle = NftConfirmStyle.Destructive,
        confirmEnabled = uiState.canBurn,
        inProgress = uiState.outcome == NftOperationOutcome.Running,
        onConfirm = onBurn,
        onCancel = onCancel,
    )
}

private val previewState = NftBurnUiState(
    name = "WIN 5000 USDT #4471",
    isDomain = false,
    burnAddress = "UQAA…AJKZ",
    fee = "≈ 0.03 TON · $0.15",
    insufficientBalance = null,
    canBurn = true,
    outcome = NftOperationOutcome.Idle,
)

@Composable
private fun PreviewContent(uiState: NftBurnUiState) {
    ComposeAppTheme {
        NftBurnSheetContent(uiState = uiState, onBurn = {}, onCancel = {})
    }
}

@Preview
@Composable
private fun NftBurnSheetNftPreview() = PreviewContent(previewState)

@Preview
@Composable
private fun NftBurnSheetDomainPreview() = PreviewContent(previewState.copy(name = "piratecash.ton", isDomain = true))

@Preview
@Composable
private fun NftBurnSheetInsufficientPreview() = PreviewContent(
    previewState.copy(
        canBurn = false,
        insufficientBalance = TranslatableString.PlainString("Not enough TON: 0.05 TON required, 0.01 TON available."),
    ),
)

@Preview
@Composable
private fun NftBurnSheetBurningPreview() = PreviewContent(
    previewState.copy(canBurn = false, outcome = NftOperationOutcome.Running),
)
