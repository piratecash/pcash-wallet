package cash.p.terminal.modules.zcashmigration

import cash.p.terminal.resources.ic_migrate_24
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.HSCaution
import cash.p.terminal.modules.fee.FeeCell
import cash.p.terminal.modules.fee.FeeItem
import cash.p.terminal.modules.send.ConfirmAmountCell
import cash.p.terminal.modules.send.SendButton
import cash.p.terminal.modules.send.SendResult
import cash.p.terminal.ui.compose.components.SectionTitleCell
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import io.horizontalsystems.core.Translator
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.wallet.entities.Coin
import kotlinx.coroutines.delay
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_attention_20
import cash.p.terminal.resources.Send_Fee
import cash.p.terminal.resources.Send_Fee_Info
import cash.p.terminal.resources.Send_Success
import cash.p.terminal.resources.balance_zcash_migration_migrate
import cash.p.terminal.resources.transactions_migrate
import cash.p.terminal.resources.transactions_migrate_to_ironwood
import cash.p.terminal.resources.zcash_migration_confirm_title
import cash.p.terminal.resources.zcash_migration_publicly_visible_description
import cash.p.terminal.resources.zcash_migration_publicly_visible_title

private const val SUCCESS_CLOSE_DELAY_MILLIS = 1200L

@Composable
internal fun ZcashMigrationConfirmScreen(
    uiState: ZcashMigrationUiState,
    coin: Coin,
    onMigrateClick: () -> Unit,
    onClose: () -> Unit,
) {
    SendResultHandler(uiState.sendResult, onClose)

    Column(modifier = Modifier.fillMaxSize()) {
        AppBar(
            title = stringResource(Res.string.zcash_migration_confirm_title),
            navigationIcon = { HsBackButton(onClick = onClose) },
        )
        Box(modifier = Modifier.fillMaxSize()) {
            MigrationDetails(
                uiState = uiState,
                coin = coin,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 106.dp)
            )
            SendButton(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                sendResult = uiState.sendResult,
                onClickSend = onMigrateClick,
                enabled = uiState.migrateEnabled,
                title = stringResource(Res.string.balance_zcash_migration_migrate)
            )
        }
    }
}

@Composable
private fun MigrationDetails(
    uiState: ZcashMigrationUiState,
    coin: Coin,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        VSpacer(12.dp)
        CellUniversalLawrenceSection(
            listOf({
                SectionTitleCell(
                    title = stringResource(Res.string.transactions_migrate),
                    value = stringResource(Res.string.transactions_migrate_to_ironwood),
                    iconResId = Res.drawable.ic_migrate_24
                )
            }, {
                ConfirmAmountCell(uiState.amountFiat, uiState.amount, coin)
            })
        )
        VSpacer(16.dp)
        CellUniversalLawrenceSection {
            FeeCell(
                title = stringResource(Res.string.Send_Fee),
                info = stringResource(Res.string.Send_Fee_Info),
                value = uiState.fee?.let { FeeItem(it, uiState.feeFiat) },
                viewState = if (uiState.fee == null && uiState.error == null) {
                    ViewState.Loading
                } else {
                    null
                }
            )
        }
        VSpacer(16.dp)
        TextImportantWarning(
            modifier = Modifier.padding(horizontal = 16.dp),
            title = stringResource(Res.string.zcash_migration_publicly_visible_title),
            text = stringResource(Res.string.zcash_migration_publicly_visible_description),
            icon = Res.drawable.ic_attention_20
        )
        // The send failure is shown here as well: its snackbar disappears and would otherwise
        // leave a disabled button with no explanation.
        val caution = uiState.error ?: (uiState.sendResult as? SendResult.Failed)?.caution
        caution?.let {
            VSpacer(16.dp)
            TextImportantError(
                modifier = Modifier.padding(horizontal = 16.dp),
                text = it.displayText,
                icon = Res.drawable.ic_attention_20
            )
        }
    }
}

@Composable
private fun SendResultHandler(
    sendResult: SendResult?,
    onSuccess: () -> Unit,
) {
    val hud = rememberHudMessenger()
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    // Caution strings are @Composable, so they must be resolved outside the LaunchedEffect.
    val failedMessage = (sendResult as? SendResult.Failed)?.caution?.displayText

    LaunchedEffect(sendResult) {
        when (sendResult) {
            is SendResult.Sent -> {
                hud.showSuccess(Translator.getString(Res.string.Send_Success), SnackbarDuration.LONG)
                delay(SUCCESS_CLOSE_DELAY_MILLIS)
                currentOnSuccess()
            }

            is SendResult.Failed -> hud.showError(failedMessage.orEmpty())

            else -> Unit
        }
    }
}

private val HSCaution.displayText: String
    @Composable get() = getDescription() ?: getString()
