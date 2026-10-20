package cash.p.terminal.modules.declinedtokens

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import cash.p.terminal.strings.helpers.TranslatableString
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.managers.DeclinedToken
import cash.p.terminal.core.managers.normalizedTokenLabel
import cash.p.terminal.modules.backuplocal.fullbackup.WalletDeclinedTokens
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_attention_24
import cash.p.terminal.strings.helpers.shorten
import cash.p.terminal.ui.extensions.BottomSheetSelectorMultiple
import cash.p.terminal.ui.extensions.BottomSheetSelectorMultipleSheet
import cash.p.terminal.ui.extensions.BottomSheetSelectorViewItem
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ImageSource
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.title
import cash.p.terminal.resources.AddToken_Decimals
import cash.p.terminal.resources.declined_tokens_add_all
import cash.p.terminal.resources.declined_tokens_count
import cash.p.terminal.resources.declined_tokens_description
import cash.p.terminal.resources.declined_tokens_select
import cash.p.terminal.resources.declined_tokens_select_title
import cash.p.terminal.resources.declined_tokens_skip_all
import cash.p.terminal.resources.declined_tokens_title
import cash.p.terminal.resources.declined_tokens_wallet
import cash.p.terminal.resources.declined_tokens_warning

private const val DISPLAY_TEXT_MAX_LENGTH = 24
private const val MAX_ENUMERATED_TOKENS = 5

/** Source is untrusted: strip bidi/control chars and cap the length. */
private fun String.toDisplayText(): String =
    normalizedTokenLabel().take(DISPLAY_TEXT_MAX_LENGTH)

/** Display-only name, sanitized and truncated — never persisted. */
private fun DeclinedToken.displayName(): String =
    coinName.toDisplayText().ifEmpty { coinCode.toDisplayText() }.ifEmpty { tokenQueryId.toDisplayText() }

/** Display-only code, sanitized and truncated — never persisted. */
private fun DeclinedToken.displayCode(): String =
    coinCode.toDisplayText().ifEmpty { tokenQueryId.toDisplayText() }

/** Shows the identity that actually decides the contract/scale — the name above may claim anything. */
private fun DeclinedToken.displaySubtitle(decimalsLabel: String): String {
    val query = TokenQuery.fromId(tokenQueryId)
    val reference = query?.tokenType?.id?.substringAfterLast(':', "").orEmpty().normalizedTokenLabel()
    return listOfNotNull(
        displayCode(),
        query?.blockchainType?.title?.toDisplayText(),
        reference.takeIf { it.isNotEmpty() }?.shorten(),
        decimals?.let { "$decimalsLabel $it" },
    ).joinToString(" · ")
}

@Composable
private fun walletSubtitle(wallet: WalletDeclinedTokens): String =
    TranslatableString.ResString(Res.string.declined_tokens_wallet, wallet.accountName.toDisplayText()).getString()

@Composable
private fun enumerationText(wallets: List<WalletDeclinedTokens>): String {
    val tokens = wallets.flatMap { it.tokens }
    return if (tokens.size > MAX_ENUMERATED_TOKENS) {
        pluralStringResource(Res.plurals.declined_tokens_count, tokens.size).format(tokens.size)
    } else {
        tokens.joinToString(", ") { it.displayName() }
    }
}

private fun List<WalletDeclinedTokens>.flatTokens(): List<Pair<WalletDeclinedTokens, DeclinedToken>> =
    flatMap { wallet -> wallet.tokens.map { wallet to it } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeclinedTokensInfoBottomSheet(
    wallets: List<WalletDeclinedTokens>,
    onReview: () -> Unit,
    onAddAll: () -> Unit,
    onSkipAll: () -> Unit,
    onClose: () -> Unit,
) {
    TransparentModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        BottomSheetHeader(
            iconPainter = painterResource(Res.drawable.ic_attention_24),
            title = stringResource(Res.string.declined_tokens_title),
            onCloseClick = onClose,
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.statusWarning),
        ) {
            InfoText(
                text = TranslatableString.ResString(Res.string.declined_tokens_description, enumerationText(wallets))
                    .getString()
            )
            TextImportantWarning(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                text = stringResource(Res.string.declined_tokens_warning),
            )
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp),
                title = stringResource(Res.string.declined_tokens_select),
                onClick = onReview,
            )
            ButtonPrimaryTransparent(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                title = stringResource(Res.string.declined_tokens_add_all),
                onClick = onAddAll,
            )
            ButtonPrimaryTransparent(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                title = stringResource(Res.string.declined_tokens_skip_all),
                onClick = onSkipAll,
            )
            VSpacer(20.dp)
        }
    }
}

/**
 * [selectedIndexes] is deliberately NOT a `remember` key: re-keying on it would discard the
 * restored selection it's meant to seed.
 */
@Composable
private fun rememberDeclinedTokensSelectorConfig(
    wallets: List<WalletDeclinedTokens>,
    selectedIndexes: List<Int>,
): BottomSheetSelectorMultipleSheet.Config {
    val title = stringResource(Res.string.declined_tokens_select_title)
    val description = stringResource(Res.string.declined_tokens_warning)
    val decimalsLabel = stringResource(Res.string.AddToken_Decimals)
    val walletHeaders = if (wallets.size > 1) wallets.associate { it.accountId to walletSubtitle(it) } else null

    return remember(wallets) {
        val flatTokens = wallets.flatTokens()
        BottomSheetSelectorMultipleSheet.Config(
            icon = ImageSource.Local(Res.drawable.ic_attention_24),
            title = title,
            selectedIndexes = selectedIndexes,
            viewItems = flatTokens.mapIndexed { index, (wallet, token) ->
                val isFirstOfWallet = index == 0 || flatTokens[index - 1].first.accountId != wallet.accountId
                BottomSheetSelectorViewItem(
                    title = token.displayName(),
                    subtitle = token.displaySubtitle(decimalsLabel),
                    // Tapping the row copies the full identity, so it can be checked in an explorer.
                    copyableString = token.tokenQueryId,
                    header = if (isFirstOfWallet) walletHeaders?.get(wallet.accountId) else null,
                )
            },
            description = description,
            allowEmpty = true,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeclinedTokensSelectBottomSheet(
    wallets: List<WalletDeclinedTokens>,
    onConfirm: (Map<String, Set<String>>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Survives a locale/theme/font-scale change, which drops everything the selector remembers.
    var selectedIndexes by rememberSaveable(wallets) { mutableStateOf(emptyList<Int>()) }
    val config = rememberDeclinedTokensSelectorConfig(wallets, selectedIndexes)
    var completed by remember(wallets) { mutableStateOf(false) }

    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        BottomSheetSelectorMultiple(
            config = config,
            onItemsSelected = { indexes ->
                completed = true
                val flatTokens = wallets.flatTokens()
                val approvals = indexes.mapNotNull { flatTokens.getOrNull(it) }
                    .groupBy({ (wallet, _) -> wallet.accountId }, { (_, token) -> token.tokenQueryId })
                    .mapValues { it.value.toSet() }
                onConfirm(approvals)
            },
            // Also fires right after a successful selection, so only treat it as cancel if Done didn't fire first.
            onCloseClick = { if (!completed) onDismiss() },
            maxListHeight = 320.dp,
            onSelectionChange = { selectedIndexes = it },
        )
    }
}

/** Wires the review sheets to [host] so the callback plumbing exists once, not per screen. */
@Composable
fun DeclinedTokensSheets(host: DeclinedTokensReviewHost) {
    val review = host.tokenReview ?: return
    DeclinedTokensSheets(
        review = review,
        onReview = host::onReviewTokens,
        onApprove = host::onApproveTokens,
        onDismiss = host::onDismissTokenReview,
    )
}

@Composable
fun DeclinedTokensSheets(
    review: DeclinedTokensReview,
    onReview: () -> Unit,
    onApprove: (Map<String, Set<String>>) -> Unit,
    onDismiss: () -> Unit,
) {
    when (review.stage) {
        DeclinedTokensStage.Info -> DeclinedTokensInfoBottomSheet(
            wallets = review.wallets,
            onReview = onReview,
            onAddAll = { onApprove(review.allTokenIds) },
            onSkipAll = { onApprove(emptyMap()) },
            onClose = onDismiss,
        )

        DeclinedTokensStage.Select -> DeclinedTokensSelectBottomSheet(
            wallets = review.wallets,
            onConfirm = onApprove,
            onDismiss = onDismiss,
        )
    }
}
