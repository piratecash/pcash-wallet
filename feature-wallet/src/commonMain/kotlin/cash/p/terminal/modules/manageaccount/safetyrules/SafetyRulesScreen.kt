package cash.p.terminal.modules.manageaccount.safetyrules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import cash.p.terminal.ui_compose.components.ButtonsGroupWithShade
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.AnnotatedResourceString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryRed
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.TermsList
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.subhead1_leah
import cash.p.terminal.ui_compose.components.title3_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Button_IAgree
import cash.p.terminal.resources.ShowKey_PrivateKeyCopyWarning_Cancel
import cash.p.terminal.resources.ShowKey_PrivateKeyCopyWarning_Proceed
import cash.p.terminal.resources.icon_lock_48
import cash.p.terminal.resources.safety_rules_points_to_agree
import cash.p.terminal.resources.safety_rules_title
import cash.p.terminal.resources.safety_rules_warning

@Composable
fun SafetyRulesScreen(
    uiState: SafetyRulesUiState,
    onCheckboxToggle: (Int) -> Unit,
    onAgreeClick: () -> Unit,
    onRiskItClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(Res.drawable.icon_lock_48),
                            contentDescription = null,
                            tint = ComposeAppTheme.colors.brandDefault,
                            modifier = Modifier.size(24.dp)
                        )
                        HSpacer(8.dp)
                        title3_leah(text = stringResource(Res.string.safety_rules_title))
                    }
                },
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Button_Close),
                        icon = Res.drawable.ic_close_24,
                        onClick = onCancelClick
                    )
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                VSpacer(12.dp)

                val warningText = stringResource(Res.string.safety_rules_warning)
                val annotatedWarning = remember(warningText) {
                    AnnotatedResourceString.htmlToAnnotatedString(warningText)
                }
                TextImportantWarning(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = annotatedWarning
                )

                VSpacer(24.dp)

                subhead1_leah(
                    text = stringResource(Res.string.safety_rules_points_to_agree),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                TermsList(
                    terms = uiState.terms,
                    onItemClicked = onCheckboxToggle
                )
            }

            when (uiState.mode) {
                SafetyRulesModule.SafetyRulesMode.AGREE -> {
                    if (!uiState.alreadyAgreed) {
                        ButtonsGroupWithShade {
                            ButtonPrimaryYellow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                title = stringResource(Res.string.Button_IAgree),
                                enabled = uiState.agreeEnabled,
                                onClick = onAgreeClick,
                            )
                        }
                    }
                }

                SafetyRulesModule.SafetyRulesMode.COPY_CONFIRM -> {
                    ButtonsGroupWithShade {
                        Column {
                            ButtonPrimaryRed(
                                enabled = uiState.agreeEnabled,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                title = stringResource(Res.string.ShowKey_PrivateKeyCopyWarning_Proceed),
                                onClick = onRiskItClick
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            ButtonPrimaryDefault(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                                title = stringResource(Res.string.ShowKey_PrivateKeyCopyWarning_Cancel),
                                onClick = onCancelClick
                            )
                        }
                    }
                }
            }
        }
    }
}
