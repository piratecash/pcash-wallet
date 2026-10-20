package cash.p.terminal.modules.premium.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cash.p.terminal.modules.markdown.MarkdownContent
import cash.p.terminal.network.pirate.domain.enity.PremiumAccountEligibility
import cash.p.terminal.network.pirate.domain.enity.TrialPremiumResult
import cash.p.terminal.ui.compose.components.ButtonPrimaryCustomColor
import cash.p.terminal.ui_compose.components.RadialBackground
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.components.TitleCenteredTopBar
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.highlightText
import cash.p.terminal.ui_compose.entities.ViewState
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.flow.Flow
import cash.p.terminal.ui_compose.components.Subhead1
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.prem_star_launch
import cash.p.terminal.resources.premium_demo_activated
import cash.p.terminal.resources.premium_demo_activation_error_backup
import cash.p.terminal.resources.premium_demo_activation_error_watch_account
import cash.p.terminal.resources.premium_demo_activation_error_wrong_type
import cash.p.terminal.resources.premium_demo_activation_failed
import cash.p.terminal.resources.premium_demo_days_left
import cash.p.terminal.resources.premium_demo_expired
import cash.p.terminal.resources.premium_title
import cash.p.terminal.resources.premium_try_for_free
import cash.p.terminal.resources.premium_upgrade_text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPremiumScreen(
    uiState: AboutPremiumUiState,
    uiEvents: Flow<TrialPremiumResult>,
    onRetryClick: () -> Unit,
    onCloseClick: () -> Unit,
    onUrlClick: (String) -> Unit,
    onTryForFreeClick: () -> Unit
) {
    val hudMessenger = rememberHudMessenger()

    LaunchedEffect(Unit) {
        uiEvents.collect { result ->
            when (result) {
                is TrialPremiumResult.DemoActive -> {
                    hudMessenger.showSuccess(getString(Res.string.premium_demo_activated))
                }
                is TrialPremiumResult.DemoExpired -> {
                    hudMessenger.showError(getString(Res.string.premium_demo_expired))
                }
                is TrialPremiumResult.DemoError -> {
                    val errorResId = when(result.premiumAccountEligibility) {
                        PremiumAccountEligibility.NO_BACKUP -> Res.string.premium_demo_activation_error_backup
                        PremiumAccountEligibility.WATCH_ACCOUNT ->
                            Res.string.premium_demo_activation_error_watch_account
                        PremiumAccountEligibility.WRONG_TYPE -> Res.string.premium_demo_activation_error_wrong_type
                        null -> Res.string.premium_demo_activation_failed
                        PremiumAccountEligibility.ELIGIBLE -> {
                            // not possible state
                            return@collect
                        }
                    }

                    hudMessenger.showError(getString(errorResId))
                }
                else -> {}
            }
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            TitleCenteredTopBar(
                title = stringResource(Res.string.premium_title),
                onCloseClick = onCloseClick
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            RadialBackground()

            Column(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxSize()
                    .padding(bottom = if (!uiState.hasPremium && uiState.hasEligibleWallets) 70.dp else 0.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                VSpacer(24.dp)
                Image(
                    painter = painterResource(Res.drawable.prem_star_launch),
                    contentDescription = null,
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth()
                )
                VSpacer(24.dp)
                ActionText()
                VSpacer(24.dp)

                // Demo days display
                if (uiState.demoDaysLeft != null && uiState.demoDaysLeft > 0) {
                    DemoDaysDisplay(daysLeft = uiState.demoDaysLeft)
                    VSpacer(24.dp)
                }

                MarkdownContent(
                    modifier = Modifier.wrapContentHeight(),
                    viewState = uiState.viewState,
                    markdownContent = uiState.markdownContent,
                    scrollableContent = false,
                    addFooter = true,
                    onRetryClick = onRetryClick,
                    onUrlClick = onUrlClick
                )
            }

            if (!uiState.hasPremium) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(
                                        ComposeAppTheme.colors.transparent,
                                        ComposeAppTheme.colors.backgroundBase
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .background(ComposeAppTheme.colors.backgroundBase)
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ButtonPrimaryCustomColor(
                            modifier = Modifier.fillMaxWidth(),
                            title = stringResource(Res.string.premium_try_for_free),
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    ComposeAppTheme.colors.premiumGradientStart,
                                    ComposeAppTheme.colors.premiumGradientEnd,
                                )
                            ),
                            onClick = onTryForFreeClick,
                            isLoading = uiState.activationViewState == ViewState.Loading
                        )
                        VSpacer(16.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DemoDaysDisplay(daysLeft: Int) {
    Subhead1(
        text = pluralStringResource(Res.plurals.premium_demo_days_left, daysLeft).format(daysLeft),
        color = ComposeAppTheme.colors.statusWarning,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    )
}

@Composable
private fun ActionText() {
    val text = highlightText(
        text = stringResource(Res.string.premium_upgrade_text),
        textColor = ComposeAppTheme.colors.textPrimary,
        highlightPart = stringResource(Res.string.premium_title),
        highlightColor = ComposeAppTheme.colors.statusWarning
    )
    Text(
        text = text,
        style = ComposeAppTheme.typography.headline1,
        color = ComposeAppTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    )
}
