package cash.p.terminal.ui_compose.components

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.manager.ITorConnectionStatusUseCase
import cash.p.terminal.manager.ITorConnectionStatusUseCase.RestartResult
import cash.p.terminal.manager.TorViewState
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.TorPage_ConnectingProgress
import cash.p.terminal.ui_compose.R
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource as cmpStringResource
import org.koin.compose.koinInject

@Composable
internal actual fun TorStatusView(
    modifier: Modifier
) {
    val torConnectionUseCase: ITorConnectionStatusUseCase = koinInject()

    val torViewState by torConnectionUseCase.torViewState.collectAsStateWithLifecycle(
        initialValue = TorViewState(
            stateText = R.string.TorPage_Connecting,
            showRetryButton = false,
            torIsActive = false,
            showNetworkConnectionError = false
        )
    )

    val scope = rememberCoroutineScope()

    var showNetworkError by remember { mutableStateOf(false) }

    val animatedSize by animateDpAsState(
        targetValue = if (torViewState.torIsActive) 20.dp else 50.dp,
        animationSpec = tween(durationMillis = 250, easing = LinearOutSlowInEasing)
    )

    Divider(
        thickness = 1.dp,
        color = ComposeAppTheme.colors.borderDivider,
        modifier = modifier.fillMaxWidth()
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(animatedSize),
        contentAlignment = Alignment.Center
    ) {
        if (torViewState.torIsActive) {
            val startColor = ComposeAppTheme.colors.statusSuccess
            val endColor = ComposeAppTheme.colors.surfacePrimary
            val color = remember { Animatable(startColor) }
            val startTextColor = ComposeAppTheme.colors.contentOnColor
            val endTextColor = ComposeAppTheme.colors.textPrimary
            val textColor = remember { Animatable(startTextColor) }

            LaunchedEffect(Unit) {
                delay(1000)
                color.animateTo(endColor, animationSpec = tween(250, easing = LinearEasing))
                textColor.animateTo(endTextColor, animationSpec = tween(250, easing = LinearEasing))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize()
                    .background(color.value),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.Tor_TorIsActive),
                    style = ComposeAppTheme.typography.micro,
                    color = textColor.value,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeAppTheme.colors.surfacePrimary)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TorStateText(torViewState, Modifier.weight(1f))

                if (torViewState.showRetryButton) {
                    ButtonSecondaryTransparent(
                        title = stringResource(R.string.Button_Retry).toUpperCase(Locale.current),
                        onClick = {
                            scope.launch {
                                when (torConnectionUseCase.restartTor()) {
                                    RestartResult.Success -> {
                                        // Reload started, no action needed
                                    }

                                    RestartResult.NoNetworkConnection -> {
                                        showNetworkError = true
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (torViewState.showNetworkConnectionError || showNetworkError) {
        val view = LocalView.current
        LaunchedEffect(torViewState.showNetworkConnectionError, showNetworkError) {
            HudHelper.showErrorMessage(view, R.string.Hud_Text_NoInternet)
        }
    }

    LaunchedEffect(torViewState.torIsActive) {
        if (torViewState.torIsActive) {
            showNetworkError = false
        }
    }
}

@Composable
private fun TorStateText(viewState: TorViewState, modifier: Modifier = Modifier) {
    val progress = viewState.bootstrapProgress
    Text(
        text = if (progress != null) {
            cmpStringResource(Res.string.TorPage_ConnectingProgress, progress)
        } else {
            stringResource(viewState.stateText)
        },
        maxLines = 1,
        modifier = modifier,
        style = ComposeAppTheme.typography.subhead2,
        color = if (viewState.showRetryButton) {
            ComposeAppTheme.colors.statusError
        } else {
            ComposeAppTheme.colors.textPrimary
        },
    )
}

@Preview
@Composable
private fun TorStateTextProgressPreview() {
    ComposeAppTheme {
        TorStateText(
            TorViewState(
                stateText = R.string.TorPage_Connecting,
                showRetryButton = false,
                torIsActive = false,
                showNetworkConnectionError = false,
                bootstrapProgress = 45,
            )
        )
    }
}
