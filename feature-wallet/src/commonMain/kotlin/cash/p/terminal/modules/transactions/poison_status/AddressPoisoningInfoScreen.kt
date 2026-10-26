@file:Suppress("PackageNaming")

package cash.p.terminal.modules.transactions.poison_status

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.annotatedStringResource
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.BottomSheetSurface
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_grey
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.caption_grey
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.title2_leah
import cash.p.terminal.ui_compose.currentYear
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.address_poisoning_info_addresses_intro
import cash.p.terminal.resources.address_poisoning_info_bullet_1
import cash.p.terminal.resources.address_poisoning_info_bullet_2
import cash.p.terminal.resources.address_poisoning_info_bullet_3
import cash.p.terminal.resources.address_poisoning_info_description
import cash.p.terminal.resources.address_poisoning_info_goal
import cash.p.terminal.resources.address_poisoning_info_title
import cash.p.terminal.resources.footer_text
import cash.p.terminal.resources.transaction_statuses_description
import cash.p.terminal.resources.transaction_statuses_footer

@Suppress("ModifierMissing")
@Composable
fun AddressPoisoningInfoScreen(
    onClose: () -> Unit,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.transparent,
        topBar = {
            AppBar(
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Button_Close),
                        icon = Res.drawable.ic_close_24,
                        onClick = onClose,
                    )
                )
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
        ) {
            VSpacer(12.dp)
            title2_leah(stringResource(Res.string.address_poisoning_info_title))
            VSpacer(24.dp)
            HorizontalDivider(color = ComposeAppTheme.colors.borderDivider)
            VSpacer(24.dp)
            Text(
                text = annotatedStringResource(Res.string.address_poisoning_info_description),
                style = ComposeAppTheme.typography.body,
                color = ComposeAppTheme.colors.textPrimary,
            )
            VSpacer(16.dp)
            body_leah(text = stringResource(Res.string.address_poisoning_info_goal))
            VSpacer(16.dp)
            body_leah(text = stringResource(Res.string.address_poisoning_info_addresses_intro))
            VSpacer(4.dp)
            BulletPoint(stringResource(Res.string.address_poisoning_info_bullet_1))
            BulletPoint(stringResource(Res.string.address_poisoning_info_bullet_2))
            BulletPoint(stringResource(Res.string.address_poisoning_info_bullet_3))
            VSpacer(16.dp)
            body_leah(text = stringResource(Res.string.transaction_statuses_description))
            VSpacer(24.dp)

            PoisonStatus.entries.forEach { status ->
                StatusInfoEntry(status)
                VSpacer(16.dp)
            }

            body_leah(text = stringResource(Res.string.transaction_statuses_footer))
            VSpacer(24.dp)
            caption_grey(
                text = stringResource(Res.string.footer_text, currentYear()),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            VSpacer(32.dp)
        }
    }
}

@Composable
private fun BulletPoint(text: String) {
    Row(modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
        body_leah(text = "•")
        body_leah(
            text = text,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun StatusInfoEntry(status: PoisonStatus) {
    PoisonStatusInfoEntry(
        status = status,
        titleContent = { subhead1_leah(text = it) },
        descriptionContent = { body_grey(text = it) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressPoisoningInfoDialog(onDismiss: () -> Unit) {
    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        BottomSheetSurface {
            AddressPoisoningInfoScreen(onClose = onDismiss)
        }
    }
}

