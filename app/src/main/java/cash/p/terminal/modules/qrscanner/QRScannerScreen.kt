package cash.p.terminal.modules.qrscanner

import android.Manifest
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.R
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefaults
import cash.p.terminal.ui_compose.components.AppDialog
import cash.p.terminal.ui_compose.components.ButtonPrimaryTransparent
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.components.title3_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import io.horizontalsystems.core.IPinComponent
import io.horizontalsystems.core.launchExternalActivity
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import androidx.compose.ui.tooling.preview.Preview as ComposePreview

// Both thresholds were already exceeded before the paste callback was added; the detekt baseline
// suppressed them by signature, so changing the signature re-keyed those entries.
@Suppress("LongMethod", "LongParameterList")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QRScannerScreen(
    uiState: QRScannerUiState,
    title: String,
    navigation: HSNavigation,
    showPasteButton: Boolean,
    onScan: (String) -> Unit,
    onPaste: (String) -> Unit,
    onCloseClick: () -> Unit,
    onCameraPermissionSettingsClick: () -> Unit,
    onGalleryImagePicked: (String) -> Unit,
    onErrorMessageConsumed: () -> Unit,
    allowGalleryWithoutPremium: Boolean = false,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var showPermissionNeededDialog by remember {
        mutableStateOf(
            cameraPermissionState.status != PermissionStatus.Granted
        )
    }
    val pinComponent = remember { getKoinInstance<IPinComponent>() }

    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { onGalleryImagePicked(it.toString()) }
        }
    val openGallery = {
        pinComponent.launchExternalActivity {
            galleryLauncher.launch(GALLERY_MIME_TYPE)
        }
    }

    if (showPermissionNeededDialog) {
        PermissionNeededDialog(
            onOkClick = {
                cameraPermissionState.launchPermissionRequest()
                showPermissionNeededDialog = false
            },
            onCancelClick = {
                showPermissionNeededDialog = false
            }
        )
    }

    QrScannerErrorEffect(uiState.errorMessageRes, onErrorHandle = onErrorMessageConsumed)

    ComposeAppTheme {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(windowInsets)
                .background(color = ComposeAppTheme.colors.backgroundBase),
            contentAlignment = Alignment.Center
        ) {
            if (cameraPermissionState.status == PermissionStatus.Granted) {
                ScannerView(onScan)
            } else {
                Spacer(
                    Modifier
                        .fillMaxSize()
                        .background(color = ComposeAppTheme.colors.scannerBackground)
                )
                GoToSettingsBox(onCameraPermissionSettingsClick)
            }
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 50.dp, start = 16.dp, end = 16.dp)
                        .align(Alignment.TopCenter),
                    style = ComposeAppTheme.typography.headline1,
                    color = ComposeAppTheme.colors.contentOnColor
                )
            }

            QrScannerButtons(
                navigation = navigation,
                isDecodingFromImage = uiState.isDecodingFromImage,
                showPasteButton = showPasteButton,
                galleryTitle = stringResource(R.string.choose_from_photos),
                onPaste = onPaste,
                onOpenImage = openGallery,
                onCloseClick = onCloseClick,
                modifier = Modifier.align(Alignment.BottomCenter),
                allowGalleryWithoutPremium = allowGalleryWithoutPremium,
            )

            if (uiState.isDecodingFromImage) {
                LoadingOverlay()
            }
        }
    }
}

@Composable
private fun ScannerView(onScan: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val disposed = remember { AtomicBoolean(false) }
    val cameraProviderRef = remember { AtomicReference<ProcessCameraProvider?>() }
    val imageAnalysisRef = remember { AtomicReference<ImageAnalysis?>() }
    val analyzerRef = remember { AtomicReference<QrCodeAnalyzer?>() }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val cameraInitErrorText = stringResource(R.string.scan_qr_camera_initialization_failed)

    fun releaseCamera() {
        analyzerRef.getAndSet(null)?.release()
        imageAnalysisRef.getAndSet(null)?.clearAnalyzer()
        cameraProviderRef.getAndSet(null)?.unbindAll()
    }

    fun isCameraBindingDisposed() =
        disposed.get() || lifecycleOwner.lifecycle.currentState == Lifecycle.State.DESTROYED

    DisposableEffect(lifecycleOwner) {
        // Tear down on view-lifecycle destruction directly, not only on Compose
        // disposal: clearAnalyzer()/unbindAll() must run for the CameraX pipeline
        // (rooted in its internal thread) to drop the analyzer and the captured
        // scan callback, otherwise the hosting screen leaks.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                disposed.set(true)
                releaseCamera()
                cameraExecutor.shutdown()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            disposed.set(true)
            releaseCamera()
            cameraExecutor.shutdown()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(previewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(listener@{
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProviderRef.set(cameraProvider)

                if (isCameraBindingDisposed()) {
                    releaseCamera()
                    return@listener
                }

                val resolutionSelector = buildResolutionSelector()

                val preview = Preview.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setHighResolutionDisabled(false)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        imageAnalysisRef.set(analysis)
                        val analyzer =
                            createQrCodeAnalyzer(disposed, mainHandler, onScan)
                        analyzerRef.set(analyzer)
                        analysis.setAnalyzer(cameraExecutor, analyzer)
                    }

                if (isCameraBindingDisposed()) {
                    releaseCamera()
                    return@listener
                }

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                releaseCamera()
                if (!disposed.get()) {
                    cameraError = cameraInitErrorText
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )
        cameraError?.let { error ->
            CameraErrorOverlay(error)
        } ?: ScannerOverlay()
    }
}

private fun buildResolutionSelector(): ResolutionSelector =
    ResolutionSelector.Builder()
        .setResolutionStrategy(
            ResolutionStrategy(
                Size(1920, 1080),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
            )
        )
        .build()

private fun createQrCodeAnalyzer(
    disposed: AtomicBoolean,
    mainHandler: Handler,
    onScan: (String) -> Unit,
): QrCodeAnalyzer = QrCodeAnalyzer { result ->
    mainHandler.post {
        if (!disposed.get()) {
            onScan(result)
        }
    }
}

@Composable
private fun GoToSettingsBox(onCameraPermissionSettingsClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        subhead2_grey(
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp),
            text = stringResource(R.string.ScanQr_CameraPermissionDeniedText)
        )
        Spacer(Modifier.height(24.dp))
        TextPrimaryButton(
            onClick = onCameraPermissionSettingsClick,
            title = stringResource(R.string.ScanQr_GoToSettings)
        )
    }
}

@Composable
private fun TextPrimaryButton(
    title: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        color = ComposeAppTheme.colors.transparent,
        contentColor = ComposeAppTheme.colors.contentOnColor,
    ) {
        Row(
            Modifier
                .defaultMinSize(
                    minWidth = ButtonPrimaryDefaults.MinWidth,
                    minHeight = ButtonPrimaryDefaults.MinHeight
                )
                .padding(ButtonPrimaryDefaults.ContentPadding)
                .clickable(
                    onClick = onClick,
                    interactionSource = interactionSource,
                    indication = null
                ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = ComposeAppTheme.typography.headline2
                )
            }
        )
    }
}

@Composable
private fun PermissionNeededDialog(
    onOkClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    ComposeAppTheme {
        AppDialog(onDismissRequest = onCancelClick) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                title3_leah(text = stringResource(R.string.ScanQr_CameraPermission_Title))
                Spacer(Modifier.height(12.dp))
                body_leah(text = stringResource(R.string.ScanQr_PleaseGrantCameraPermission))
                Spacer(Modifier.height(32.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ButtonPrimaryTransparent(
                        onClick = onCancelClick,
                        title = stringResource(R.string.Button_Cancel)
                    )
                    Spacer(Modifier.width(8.dp))
                    ButtonPrimaryYellow(
                        onClick = onOkClick,
                        title = stringResource(R.string.Button_Ok)
                    )
                }
            }
        }
    }
}

@ComposePreview
@Composable
private fun PermissionNeededDialogPreview() {
    ComposeAppTheme {
        PermissionNeededDialog({}, {})
    }
}

@ComposePreview
@Composable
private fun ScannerOverlayPreview() {
    ComposeAppTheme {
        QRScannerScreen(
            uiState = QRScannerUiState(),
            title = "SMART SCAN",
            navigation = HSNavigation(NavBackStack()),
            showPasteButton = true,
            onScan = {},
            onPaste = {},
            onCloseClick = {},
            onCameraPermissionSettingsClick = {},
            onGalleryImagePicked = {},
            onErrorMessageConsumed = {}
        )
    }
}

private const val GALLERY_MIME_TYPE = "image/*"
