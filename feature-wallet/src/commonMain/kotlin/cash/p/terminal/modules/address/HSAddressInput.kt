package cash.p.terminal.modules.address

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.feature.wallet.qrScannerAvailable
import cash.p.terminal.navigation.rememberQrScannerLauncher
import cash.p.terminal.entities.Address
import cash.p.terminal.ui_compose.entities.DataState
import cash.p.terminal.ui.compose.components.FormsInputAddress
import cash.p.terminal.ui.compose.components.TextPreprocessor
import cash.p.terminal.ui.compose.components.TextPreprocessorImpl
import cash.p.terminal.wallet.title
import cash.p.terminal.wallet.entities.TokenQuery
import org.koin.compose.koinInject
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Send_Hint_Address
import cash.p.terminal.resources.qr_scanner_title_address
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun HSAddressInput(
    modifier: Modifier = Modifier,
    initial: Address? = null,
    tokenQuery: TokenQuery,
    coinCode: String,
    error: Throwable? = null,
    textPreprocessor: TextPreprocessor = TextPreprocessorImpl,
    navigation: HSNavigation,
    onError: ((Throwable?) -> Unit)? = null,
    onValueChange: ((Address?) -> Unit)? = null,
    isPoisonAddress: Boolean = false,
) {
    val viewModel = koinViewModel<AddressViewModel>(key = "address_view_model_${tokenQuery.id}") {
        parametersOf(tokenQuery, coinCode, initial)
    }

    HSAddressInput(
        modifier = modifier,
        viewModel = viewModel,
        inputState = viewModel.inputState.collectAsStateWithLifecycle().value,
        address = viewModel.address.collectAsStateWithLifecycle().value,
        value = viewModel.value.collectAsStateWithLifecycle().value,
        error = error,
        textPreprocessor = textPreprocessor,
        navigation = navigation,
        onError = onError,
        onValueChange = onValueChange,
        isPoisonAddress = isPoisonAddress,
    )
}

@Composable
fun HSAddressInput(
    modifier: Modifier = Modifier,
    viewModel: AddressViewModel,
    inputState: DataState<Address>?,
    address: Address?,
    value: String,
    error: Throwable? = null,
    textPreprocessor: TextPreprocessor = TextPreprocessorImpl,
    navigation: HSNavigation,
    onError: ((Throwable?) -> Unit)? = null,
    onValueChange: ((Address?) -> Unit)? = null,
    isPoisonAddress: Boolean = false,
) {
    LaunchedEffect(error) {
        viewModel.onAddressError(error)
    }

    LaunchedEffect(address) {
        onValueChange?.invoke(address)
    }

    LaunchedEffect(inputState) {
        onError?.invoke(inputState?.errorOrNull)
    }

    val scannerTitle = stringResource(Res.string.qr_scanner_title_address, viewModel.blockchainType.title)
    val qrScanner = navigation.rememberQrScannerLauncher(koinInject()) { scannedText ->
        viewModel.parseText(textPreprocessor.process(scannedText))
    }

    FormsInputAddress(
        modifier = modifier,
        value = value,
        hint = stringResource(Res.string.Send_Hint_Address),
        state = inputState,
        textPreprocessor = textPreprocessor,
        navigation = navigation,
        chooseContactEnable = viewModel.hasContacts(),
        blockchainType = viewModel.blockchainType,
        onQrScanClick = if (qrScannerAvailable()) {
            { qrScanner.open(scannerTitle) }
        } else {
            null
        },
        onValueChange = {
            viewModel.parseText(it)
        },
        isPoisonAddress = isPoisonAddress,
    )
}
