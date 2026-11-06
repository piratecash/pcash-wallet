package cash.p.terminal.modules.qrscanner

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import cash.p.terminal.core.IClipboardManager
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.rememberPremiumAction
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Send_Button_Paste
import cash.p.terminal.resources.star_filled_yellow_16
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefaultWithIcon
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun CollectQrScanResult(viewModel: QRScannerViewModel, onResult: (String) -> Unit) {
    val pageLifecycle = LocalLifecycleOwner.current.lifecycle
    val currentOnResult by rememberUpdatedState(onResult)
    LaunchedEffect(viewModel) {
        viewModel.scanResult.collectLatest { decoded ->
            // A result can arrive while this page is not resumed (app backgrounded, or a late gallery
            // decode after Back). Wait for this page's own RESUMED: once it is popped, the wait is
            // cancelled, so a late result never reaches the caller or pops it.
            pageLifecycle.withResumed { currentOnResult(decoded) }
        }
    }
}

@Composable
fun QrScannerErrorEffect(errorMessageRes: StringResource?, onErrorHandle: () -> Unit) {
    val hudMessenger = rememberHudMessenger()
    val currentOnErrorShown by rememberUpdatedState(onErrorHandle)
    errorMessageRes?.let { errorRes ->
        val errorText = stringResource(errorRes)
        LaunchedEffect(errorRes) {
            hudMessenger.showError(errorText)
            currentOnErrorShown()
        }
    }
}

@Composable
fun QrScannerButtons(
    navigation: HSNavigation,
    isDecodingFromImage: Boolean,
    showPasteButton: Boolean,
    galleryTitle: String,
    onPaste: (String) -> Unit,
    onOpenImage: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
    allowGalleryWithoutPremium: Boolean = false,
) {
    val openImageWithPremium = navigation.rememberPremiumAction(onOpenImage)

    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showPasteButton) {
            ButtonPrimaryYellow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                title = stringResource(Res.string.Send_Button_Paste),
                enabled = !isDecodingFromImage,
                onClick = {
                    onPaste(getKoinInstance<IClipboardManager>().getCopiedText())
                }
            )
        }

        if (allowGalleryWithoutPremium) {
            ButtonPrimaryDefault(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                title = galleryTitle,
                enabled = !isDecodingFromImage,
                onClick = onOpenImage
            )
        } else {
            ButtonPrimaryDefaultWithIcon(
                icon = painterResource(Res.drawable.star_filled_yellow_16),
                iconTint = ComposeAppTheme.colors.statusWarning,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                title = galleryTitle,
                enabled = !isDecodingFromImage,
                onClick = openImageWithPremium
            )
        }

        ButtonPrimaryDefault(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            title = stringResource(Res.string.Button_Cancel),
            enabled = !isDecodingFromImage,
            onClick = onCloseClick
        )
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
fun LoadingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(ComposeAppTheme.colors.backgroundBase.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = ComposeAppTheme.colors.textPrimary
        )
    }
}

@Composable
fun CameraErrorOverlay(errorMessage: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ComposeAppTheme.colors.scannerBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            subhead2_grey(
                textAlign = TextAlign.Center,
                text = errorMessage
            )
        }
    }
}

@Composable
fun ScannerOverlay(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val scanWindowSize = with(density) { 250.dp.toPx() }
    val cornerLength = with(density) { 30.dp.toPx() }
    val strokeWidth = with(density) { 4.dp.toPx() }
    val cornerColor = ComposeAppTheme.colors.brandDefault

    // Animated scan line
    val infiniteTransition = rememberInfiniteTransition(label = "scanLine")
    val scanLinePosition by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanLinePosition"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Calculate scan window position (centered)
        val scanLeft = (canvasWidth - scanWindowSize) / 2
        val scanTop = (canvasHeight - scanWindowSize) / 2
        val scanRight = scanLeft + scanWindowSize
        val scanBottom = scanTop + scanWindowSize

        val overlayColor = Color.Black.copy(alpha = 0.6f)

        // Create cutout path for the scan window
        val cutoutPath = Path().apply {
            addRect(Rect(scanLeft, scanTop, scanRight, scanBottom))
        }

        // Draw overlay with cutout using clipPath with Difference operation
        clipPath(cutoutPath, clipOp = ClipOp.Difference) {
            drawRect(color = overlayColor)
        }

        val inset = strokeWidth / 2
        val left = scanLeft + inset
        val top = scanTop + inset
        val right = scanRight - inset
        val bottom = scanBottom - inset
        drawCornerBracket(Offset(left, top), cornerLength, cornerLength, cornerColor, strokeWidth)
        drawCornerBracket(Offset(right, top), -cornerLength, cornerLength, cornerColor, strokeWidth)
        drawCornerBracket(Offset(left, bottom), cornerLength, -cornerLength, cornerColor, strokeWidth)
        drawCornerBracket(Offset(right, bottom), -cornerLength, -cornerLength, cornerColor, strokeWidth)

        // Draw animated red scan line
        val padding = 16.dp.toPx()
        val lineY = scanTop + padding + (scanWindowSize - 2 * padding) * scanLinePosition
        drawLine(
            color = Color.Red,
            start = Offset(scanLeft + padding, lineY),
            end = Offset(scanRight - padding, lineY),
            strokeWidth = 2.dp.toPx()
        )
    }
}

/** An L-shaped bracket whose arms run from [corner] by [armX] horizontally and [armY] vertically. */
private fun DrawScope.drawCornerBracket(
    corner: Offset,
    armX: Float,
    armY: Float,
    color: Color,
    strokeWidth: Float,
) {
    val path = Path().apply {
        moveTo(corner.x, corner.y + armY)
        lineTo(corner.x, corner.y)
        lineTo(corner.x + armX, corner.y)
    }
    drawPath(path, color, style = Stroke(width = strokeWidth))
}
