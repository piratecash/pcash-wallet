package cash.p.terminal.modules.moneroconfigure

import android.os.Parcelable
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.R
import cash.p.terminal.modules.enablecoin.restoresettings.BirthdayHeightConfigUiState
import cash.p.terminal.modules.enablecoin.restoresettings.TokenConfig
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.FormsInput
import cash.p.terminal.ui.compose.components.RestoreHeightInput
import cash.p.terminal.ui.compose.components.SelectDateBottomSheet
import cash.p.terminal.ui.compose.components.restoreGenesisDateMillis
import cash.p.terminal.ui.compose.components.restoreMaxDateMillis
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.HeaderText
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RestoreHeightMode
import cash.p.terminal.ui_compose.components.RestoreHeightScreen
import cash.p.terminal.ui_compose.components.caption_lucian
import cash.p.terminal.ui_compose.components.title3_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.chartview.rememberAsyncImagePainterWithFallback
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.imageUrl
import java.time.LocalDate
import kotlinx.parcelize.Parcelize
import org.koin.compose.viewmodel.koinViewModel

class MoneroConfigurePage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        OneShotBackHandler { close(navigation) }
        val initialConfig = input.initialConfig
        val viewModel: MoneroConfigureViewModel = koinViewModel()
        LaunchedEffect(initialConfig) {
            viewModel.setInitialConfig(initialConfig)
        }
        MoneroConfigureRoute(
            onCloseWithResult = {
                viewModel.onClosed()
                closeWithConfig(it, navigation)
            },
            onCloseClick = { close(navigation) },
            onModeSelect = viewModel::onModeSelect,
            onSetBirthdayHeight = viewModel::setBirthdayHeight,
            onDatePick = viewModel::onDatePicked,
            onDoneClick = viewModel::onDoneClick,
            uiState = viewModel.uiState,
        )
    }

    private fun closeWithConfig(config: TokenConfig, navigation: HSNavigation) {
        navigation.setResult(this, Result(config))
        navigation.navigateUp()
    }

    private fun close(navigation: HSNavigation) {
        navigation.setResult(this, Result(null))
        navigation.navigateUpSafely()
    }

    @Parcelize
    data class Result(val config: TokenConfig?) : Parcelable

    @Parcelize
    data class Input(val initialConfig: TokenConfig?) : Parcelable
}

// Handles only the first system back; later ones fall through to the default pop.
@Composable
internal fun OneShotBackHandler(onBack: () -> Unit) {
    var enabled by remember { mutableStateOf(true) }
    BackHandler(enabled = enabled) {
        enabled = false
        onBack()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneroConfigureRoute(
    onCloseClick: () -> Unit,
    onCloseWithResult: (TokenConfig) -> Unit,
    onModeSelect: (RestoreHeightMode) -> Unit,
    onSetBirthdayHeight: (String) -> Unit,
    onDoneClick: () -> Unit,
    uiState: BirthdayHeightConfigUiState,
    title: String? = null,
    blockchainType: BlockchainType = BlockchainType.Monero,
    @StringRes heightHintRes: Int = R.string.restoreheight_hint,
    windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
    onDatePick: ((LocalDate) -> Unit)? = null,
) {

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(uiState.closeWithResult) {
        uiState.closeWithResult?.let {
            keyboardController?.hide()
            onCloseWithResult(it)
        }
    }

    RestoreHeightScreen(
        mode = uiState.mode,
        doneEnabled = uiState.doneEnabled,
        onDoneClick = onDoneClick,
        onModeSelect = { mode ->
            onModeSelect(mode)
            focusManager.clearFocus()
        },
        topBar = {
            BirthdayHeightAppBar(
                title = title ?: stringResource(R.string.restore_monero),
                blockchainType = blockchainType,
                onCloseClick = onCloseClick
            )
        },
        contentWindowInsets = windowInsets,
        existingWalletContent = {
            BirthdayHeightSection(
                uiState = uiState,
                heightHintRes = heightHintRes,
                onSetBirthdayHeight = onSetBirthdayHeight,
                onDatePick = onDatePick,
            )
        },
    )
}

@Composable
private fun BirthdayHeightSection(
    uiState: BirthdayHeightConfigUiState,
    @StringRes heightHintRes: Int,
    onSetBirthdayHeight: (String) -> Unit,
    onDatePick: ((LocalDate) -> Unit)?,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column {
        if (showDatePicker && onDatePick != null) {
            SelectDateBottomSheet(
                initialDateMillis = null,
                minDateMillis = restoreGenesisDateMillis(BlockchainType.Monero),
                maxDateMillis = restoreMaxDateMillis(),
                onDateSelect = onDatePick,
                onDismiss = { showDatePicker = false }
            )
        }
        Spacer(Modifier.height(16.dp))
        HeaderText(stringResource(id = R.string.restoreheight_title))
        if (onDatePick != null) {
            RestoreHeightInput(
                modifier = Modifier.padding(horizontal = 16.dp),
                initial = uiState.birthdayHeight,
                hint = stringResource(heightHintRes),
                error = uiState.errorHeight,
                pasteEnabled = false,
                onValueChange = onSetBirthdayHeight,
                onCalendarClick = { showDatePicker = true },
            )
        } else {
            FormsInput(
                modifier = Modifier.padding(horizontal = 16.dp),
                initial = uiState.birthdayHeight,
                pasteEnabled = false,
                singleLine = true,
                hint = stringResource(heightHintRes),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done
                ),
                onValueChange = onSetBirthdayHeight
            )
            uiState.errorHeight?.let { errorText ->
                Spacer(Modifier.height(8.dp))
                caption_lucian(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    text = errorText
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun BirthdayHeightAppBar(
    title: String,
    blockchainType: BlockchainType,
    onCloseClick: () -> Unit,
) {
    AppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(24.dp),
                    painter = rememberAsyncImagePainterWithFallback(
                        model = blockchainType.imageUrl,
                        error = painterResource(R.drawable.ic_platform_placeholder_32)
                    ),
                    contentDescription = null
                )
                title3_leah(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        menuItems = listOf(
            MenuItem(
                title = TranslatableString.ResString(R.string.Button_Close),
                icon = R.drawable.ic_close_24,
                onClick = onCloseClick
            )
        )
    )
}

@Preview
@Composable
private fun Preview_MoneroConfigure() {
    ComposeAppTheme(darkTheme = false) {
        MoneroConfigureRoute(
            onCloseClick = {},
            onCloseWithResult = {},
            onModeSelect = {},
            onSetBirthdayHeight = {},
            onDoneClick = {},
            uiState = BirthdayHeightConfigUiState(birthdayHeight = ""),
        )
    }
}
