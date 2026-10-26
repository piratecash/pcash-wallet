package cash.p.terminal.modules.transactions

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.launch
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.alpha_aml_title
import cash.p.terminal.resources.aml_check_info_description
import cash.p.terminal.resources.aml_check_info_later
import cash.p.terminal.resources.ic_star_filled_20
import cash.p.terminal.resources.premium_settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmlCheckInfoBottomSheet(
    onPremiumSettingsClick: () -> Unit,
    onLaterClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    TransparentModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BottomSheetHeader(
            iconPainter = painterResource(Res.drawable.ic_star_filled_20),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.statusWarning),
            title = stringResource(Res.string.alpha_aml_title),
            titleColor = ComposeAppTheme.colors.textPrimary,
            onCloseClick = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    onDismiss()
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, ComposeAppTheme.colors.borderDefault, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                body_leah(text = stringResource(Res.string.aml_check_info_description))
            }
            Spacer(Modifier.height(8.dp))
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 12.dp, end = 24.dp),
                title = stringResource(Res.string.premium_settings),
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onPremiumSettingsClick()
                    }
                }
            )
            ButtonPrimaryTransparent(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 12.dp, end = 24.dp),
                title = stringResource(Res.string.aml_check_info_later),
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onLaterClick()
                    }
                }
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

