package cash.p.terminal.modules.tonconnect

import cash.p.terminal.ui_compose.components.TitleValueCell
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.core.description
import cash.p.terminal.core.rememberAuthorizedAction
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.modules.evmfee.ButtonsGroupWithShade
import cash.p.terminal.modules.walletconnect.session.ui.DropDownCell
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Button_Cancel
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Button_Connect
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.TonConnect_ChooseWallet
import cash.p.terminal.resources.TonConnect_Error_InvalidUrl
import cash.p.terminal.resources.TonConnect_Title
import cash.p.terminal.resources.TonConnect_Url
import cash.p.terminal.resources.TonConnect_Wallet
import cash.p.terminal.resources.WalletConnect_Approve_Hint
import cash.p.terminal.resources.WalletConnect_Error_NoSuitableAccount
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.resources.ic_platform_placeholder_24
import cash.p.terminal.resources.manifest_loading
import cash.p.terminal.resources.tonconnect_wallet_version
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppSelectorDialog
import cash.p.terminal.ui_compose.components.AppSelectorItem
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefault
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.TextImportantError
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun TonConnectNewScreen(
    navigation: HSNavigation,
    uri: String,
    onResult: (Boolean) -> Unit,
) {
    val viewModel = viewModel<TonConnectNewViewModel>(initializer = {
        TonConnectNewViewModel(
            uri = uri,
            tonConnectManager = getKoinInstance()
        )
    })

    val connect = navigation.rememberAuthorizedAction(input = null) { viewModel.connect() }
    val context = LocalContext.current
    val uiState = viewModel.uiState

    LaunchedEffect(uiState.finish) {
        if (uiState.finish) {
            onResult.invoke(true)
        }
    }

    LaunchedEffect(uiState.toast) {
        uiState.toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.onToastShow()
        }
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.TonConnect_Title),
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Button_Close),
                        icon = Res.drawable.ic_close_24,
                        enabled = !uiState.connecting,
                        onClick = { navigation.navigateUpSafely() }
                    )
                )
            )
        }
    ) {
        Column(modifier = Modifier.padding(it)) {
            Row(
                modifier = Modifier.padding(
                    top = 12.dp,
                    start = 24.dp,
                    end = 24.dp,
                    bottom = 24.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(15.dp)),
                    painter = uiState.manifest?.let {
                        rememberDAppIconPainter(it)
                    } ?: painterResource(Res.drawable.ic_platform_placeholder_24),
                    contentDescription = null,
                )
                Text(
                    modifier = Modifier.padding(start = 16.dp),
                    text = uiState.manifest?.name ?: "",
                    style = ComposeAppTheme.typography.headline1,
                    color = ComposeAppTheme.colors.textPrimary
                )
            }

            var showSortTypeSelectorDialog by remember { mutableStateOf(false) }
            if (showSortTypeSelectorDialog) {
                AppSelectorDialog(
                    title = stringResource(Res.string.TonConnect_ChooseWallet),
                    items = uiState.accounts.map { account ->
                        AppSelectorItem(
                            title = account.name,
                            selected = account == uiState.account,
                            item = account,
                        )
                    },
                    onDismiss = {
                        showSortTypeSelectorDialog = false
                    },
                    onSelect = viewModel::onSelectAccount
                )
            }

            var showWalletVersionSelector by remember { mutableStateOf(false) }
            if (showWalletVersionSelector) {
                AppSelectorDialog(
                    title = stringResource(Res.string.tonconnect_wallet_version),
                    items = uiState.walletVersions.map { version ->
                        AppSelectorItem(
                            title = version.title,
                            selected = version == uiState.walletVersion,
                            item = version,
                            subtitle = version.description,
                        )
                    },
                    onDismiss = {
                        showWalletVersionSelector = false
                    },
                    onSelect = viewModel::onSelectWalletVersion
                )
            }

            CellUniversalLawrenceSection(
                buildList<@Composable () -> Unit> {
                    add {
                        val url = uiState.manifest?.host ?: ""
                        TitleValueCell(stringResource(Res.string.TonConnect_Url), url)
                    }
                    add {
                        DropDownCell(
                            stringResource(Res.string.TonConnect_Wallet),
                            uiState.account?.name
                                ?: stringResource(Res.string.TonConnect_ChooseWallet),
                            enabled = true,
                            onSelect = {
                                showSortTypeSelectorDialog = true
                            }
                        )
                    }
                    if (uiState.walletVersions.isNotEmpty()) {
                        add {
                            DropDownCell(
                                stringResource(Res.string.tonconnect_wallet_version),
                                uiState.walletVersion.title,
                                enabled = true,
                                onSelect = {
                                    showWalletVersionSelector = true
                                }
                            )
                        }
                    }
                }
            )

            Spacer(Modifier.height(12.dp))

            if (uiState.error != null) {
                TextImportantError(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = when (uiState.error) {
                        is InvalidRequestError -> stringResource(Res.string.TonConnect_Error_InvalidUrl)
                        is NoTonAccountError -> stringResource(Res.string.WalletConnect_Error_NoSuitableAccount)
                        else -> uiState.error.message?.nullIfBlank() ?: uiState.error.javaClass.simpleName
                    }
                )
            } else {
                TextImportantWarning(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    text = stringResource(Res.string.WalletConnect_Approve_Hint)
                )
            }

            Spacer(Modifier.weight(1f))
            ButtonsGroupWithShade {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    val manifestLoading = uiState.manifest == null && uiState.error == null
                    ButtonPrimaryYellow(
                        modifier = Modifier.fillMaxWidth(),
                        title = if(manifestLoading) {
                            stringResource(Res.string.manifest_loading)
                        } else {
                            stringResource(Res.string.Button_Connect)
                        },
                        onClick = connect,
                        enabled = uiState.connectEnabled && uiState.manifest != null,
                        loadingIndicator = manifestLoading || uiState.connecting
                    )
                    VSpacer(16.dp)
                    ButtonPrimaryDefault(
                        modifier = Modifier.fillMaxWidth(),
                        title = stringResource(Res.string.Button_Cancel),
                        enabled = !uiState.connecting,
                        onClick = {
                            viewModel.reject()
                            onResult.invoke(false)
                        }
                    )
                }
            }
        }
    }
}

fun String.nullIfBlank(): String? {
    if (this.isBlank()) return null
    return this
}
