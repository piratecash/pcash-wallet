package cash.p.terminal.modules.settings.addresschecker

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.TransactionSettingsStorage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SettingsAddressChecker_CheckAddress
import cash.p.terminal.resources.SettingsAddressChecker_CheckTheRecipientInfo
import cash.p.terminal.resources.SettingsAddressChecker_RecipientCheck
import cash.p.terminal.resources.address_checker_title
import cash.p.terminal.resources.ic_arrow_right
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.InfoText
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.SwitchWithText
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.ViewModelUiState
import org.koin.compose.viewmodel.koinViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

class AddressCheckerPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        AddressCheckerScreen(
            onCheckAddressClick = {
                navigation.slideFromRight(AddressCheckPage())
            },
            onClose = navigation::navigateUpSafely
        )
    }

}

@Composable
fun AddressCheckerScreen(
    onCheckAddressClick: () -> Unit,
    onClose: () -> Unit
) {
    val viewModel = koinViewModel<AddressCheckerViewModel>()
    val uiState = viewModel.uiState
    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.address_checker_title),
                navigationIcon = {
                    HsBackButton(onClick = onClose)
                },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            SectionUniversalLawrence {
                SwitchWithText(
                    text = stringResource(Res.string.SettingsAddressChecker_RecipientCheck),
                    checked = uiState.checkEnabled,
                    onCheckedChange = viewModel::toggleAddressChecking
                )
            }
            InfoText(
                text = stringResource(Res.string.SettingsAddressChecker_CheckTheRecipientInfo),
            )
            VSpacer(12.dp)
            CellUniversalLawrenceSection(
                listOf({
                    RowUniversal(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onClick = onCheckAddressClick
                    ) {
                        body_leah(
                            text = stringResource(Res.string.SettingsAddressChecker_CheckAddress),
                            maxLines = 1,
                        )
                        Spacer(Modifier.weight(1f))
                        Image(
                            modifier = Modifier.size(20.dp),
                            painter = painterResource(Res.drawable.ic_arrow_right),
                            contentDescription = null,
                        )
                    }
                })
            )
            VSpacer(32.dp)
        }
    }
}

class AddressCheckerViewModel(
    private val localStorage: TransactionSettingsStorage,
) : ViewModelUiState<AddressCheckerUiState>() {
    private var checkEnabled = localStorage.recipientAddressBaseCheckEnabled

    override fun createState() = AddressCheckerUiState(
        checkEnabled = checkEnabled
    )

    fun toggleAddressChecking(enabled: Boolean) {
        localStorage.recipientAddressBaseCheckEnabled = enabled
        checkEnabled = enabled
        emitState()
    }
}

data class AddressCheckerUiState(
    val checkEnabled: Boolean
)
