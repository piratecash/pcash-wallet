package cash.p.terminal.ui.compose.components

import cash.p.terminal.resources.ic_attention_24
import cash.p.terminal.resources.Res
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cash.p.terminal.modules.balance.SyncingProgress
import cash.p.terminal.modules.balance.SyncingProgressType
import cash.p.terminal.modules.balance.formatProgressPercent
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Token

@Composable
fun CoinIconWithSyncProgress(
    token: Token,
    syncingProgress: SyncingProgress,
    failedIconVisible: Boolean,
    onClickSyncError: (() -> Unit)?,
    boxSize: Dp = 40.dp,
    iconSize: Dp = 32.dp
) {
    val transition = rememberInfiniteTransition(label = "sync_rotation")
    val rotate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1100,
                easing = LinearEasing
            )
        ),
        label = "rotation"
    )

    val progress = syncingProgress.progress
    val iconAlpha = if (syncingProgress.type == null) 1f else 0.3f
    val ringColor = ComposeAppTheme.colors.iconPrimary
    val circleColor = ComposeAppTheme.colors.surfacePlaceholder

    Box(
        modifier = Modifier
            .size(boxSize)
            .drawBehind {
                when (syncingProgress.type) {
                    SyncingProgressType.ProgressWithRing -> {
                        val progressF = ((progress ?: 0.0).coerceAtLeast(10.0) / 100.0).toFloat()
                        val angle = 360f * progressF

                        inset(-1.dp.toPx()) {
                            drawArc(
                                color = circleColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                            rotate(degrees = -90f) {
                                drawArc(
                                    color = ringColor,
                                    startAngle = 0f,
                                    sweepAngle = angle,
                                    useCenter = false,
                                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }
                    }

                    SyncingProgressType.Spinner -> {
                        inset(-1.dp.toPx()) {
                            drawArc(
                                color = circleColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                            rotate(degrees = rotate) {
                                drawArc(
                                    color = ringColor,
                                    startAngle = 0f,
                                    sweepAngle = -120f,
                                    useCenter = false,
                                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }
                    }

                    null -> {
                        // No progress indicator
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (failedIconVisible) {
            val clickableModifier = if (onClickSyncError != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(
                        bounded = false,
                        radius = 20.dp
                    ),
                    onClick = onClickSyncError
                )
            } else {
                Modifier
            }

            Image(
                modifier = Modifier
                    .size(iconSize)
                    .then(clickableModifier),
                painter = painterResource(Res.drawable.ic_attention_24),
                contentDescription = "coin icon",
                colorFilter = ColorFilter.tint(ComposeAppTheme.colors.statusError)
            )
        } else {
            CoinImage(
                token = token,
                modifier = Modifier
                    .size(iconSize)
                    .alpha(iconAlpha)
            )
        }

        if (syncingProgress.type == SyncingProgressType.ProgressWithRing) {
            syncingProgress.progress?.let {
                SyncProgressText("${formatProgressPercent(it)}%")
            }
        }
    }
}

@Composable
internal fun SyncProgressText(text: String) {
    BasicText(
        text = text,
        style = ComposeAppTheme.typography.subhead2.copy(
            color = ComposeAppTheme.colors.textPrimary
        ),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(
            minFontSize = 8.sp,
            maxFontSize = 14.sp,
            stepSize = 1.sp
        )
    )
}
