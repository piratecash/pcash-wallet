package cash.p.terminal.modules.manageaccount.showextendedkey

import io.horizontalsystems.hdwalletkit.HDKeychain
import io.horizontalsystems.core.common.CommonParcelable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Icon
import cash.p.terminal.resources.Info_Title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_info_24
import cash.p.terminal.ui_compose.components.AppModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material3.Scaffold
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.feature.wallet.FaqTopic
import cash.p.terminal.feature.wallet.openFaq
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.modules.manageaccount.showextendedkey.ShowExtendedKeyModule.DisplayKeyType
import cash.p.terminal.modules.manageaccount.ui.ActionButton
import cash.p.terminal.modules.manageaccount.ui.ConfirmCopyBottomSheet
import cash.p.terminal.modules.manageaccount.ui.HidableContent
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HSpacer
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.AppSelectorDialog
import cash.p.terminal.ui_compose.components.AppSelectorItem
import cash.p.terminal.ui_compose.components.TextImportantWarning
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead1_grey
import cash.p.terminal.modules.manageaccount.ui.rememberCopyWithHud
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.hdwalletkit.HDExtendedKey
import kotlinx.coroutines.launch
import io.horizontalsystems.core.common.CommonParcelize
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Alert_Copy
import cash.p.terminal.resources.ExtendedKey_Account
import cash.p.terminal.resources.ExtendedKey_Blockchain
import cash.p.terminal.resources.ExtendedKey_Purpose
import cash.p.terminal.resources.ExtendedKey_TapToShowPrivateKey
import cash.p.terminal.resources.PrivateKeys_NeverShareWarning
import cash.p.terminal.resources.ic_down_arrow_20
import cash.p.terminal.resources.ic_info_20

class ShowExtendedKeyPage(val input: Input) : HSPage(screenshotEnabled = false) {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val hdExtendedKey = input.extendedRootKey
        val displayKeyType = input.displayKeyType

        if (hdExtendedKey == null) {
            NoExtendKeyScreen()
        } else {
            ShowExtendedKeyScreen(
                navigation,
                hdExtendedKey,
                displayKeyType
            )
        }
    }

    @CommonParcelize
    data class Input(val extendedRootKeySerialized: String, val displayKeyType: DisplayKeyType) : CommonParcelable {
        val extendedRootKey: HDExtendedKey?
            get() = try {
                HDExtendedKey(extendedRootKeySerialized)
            } catch (error: Throwable) {
                null
            }

        constructor(extendedRootKey: HDExtendedKey, displayKeyType: DisplayKeyType) : this(
            extendedRootKey.serialize(), displayKeyType
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ShowExtendedKeyScreen(
    navigation: HSNavigation,
    extendedKey: HDExtendedKey,
    displayKeyType: DisplayKeyType
) {
    val viewModel =
        viewModel {
            ShowExtendedKeyViewModel(
                keyChain = HDKeychain(extendedKey.key),
                displayKeyType = displayKeyType,
                purpose = extendedKey.purposes.first(),
                extendedKeyCoinType = extendedKey.coinTypes.first()
            )
        }

    val copyWithHud = rememberCopyWithHud()
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(
        initialValue = ModalBottomSheetValue.Hidden,
    )

    AppModalBottomSheetLayout(
        sheetState = sheetState,
        sheetContent = {
            ConfirmCopyBottomSheet(
                onConfirm = {
                    coroutineScope.launch {
                        copyWithHud(viewModel.extendedKey)
                        sheetState.hide()
                    }
                },
                onCancel = {
                    coroutineScope.launch {
                        sheetState.hide()
                    }
                }
            )
        }
    ) {
        Scaffold(
            containerColor = ComposeAppTheme.colors.backgroundBase,
            topBar = {
                AppBar(
                    title = viewModel.title.getString(),
                    navigationIcon = {
                        HsBackButton(onClick = navigation::navigateUpSafely)
                    },
                    menuItems = listOf(
                        MenuItem(
                            title = TranslatableString.ResString(Res.string.Info_Title),
                            icon = Res.drawable.ic_info_24,
                            onClick = {
                                navigation.openFaq(FaqTopic.PrivateKeys)
                            }
                        )
                    )
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier.padding(paddingValues),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Top
                ) {
                    VSpacer(12.dp)

                    if (viewModel.displayKeyType.isPrivate) {
                        TextImportantWarning(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            text = stringResource(Res.string.PrivateKeys_NeverShareWarning)
                        )
                        VSpacer(24.dp)
                    }

                    var showBlockchainSelectorDialog by remember { mutableStateOf(false) }
                    var showPurposeSelectorDialog by remember { mutableStateOf(false) }
                    var showAccountSelectorDialog by remember { mutableStateOf(false) }

                    val menuItems = buildList<@Composable () -> Unit> {
                        add {
                            MenuItem(
                                title = stringResource(Res.string.ExtendedKey_Purpose),
                                value = viewModel.purpose.name,
                                onClick = if (
                                    viewModel.displayKeyType == DisplayKeyType.Bip32RootKey ||
                                    viewModel.displayKeyType.isDerivable
                                ) {
                                    { showPurposeSelectorDialog = true }
                                } else {
                                    null
                                }
                            )
                        }
                        if (viewModel.displayKeyType.isDerivable) {
                            add {
                                MenuItem(
                                    title = stringResource(Res.string.ExtendedKey_Blockchain),
                                    value = viewModel.blockchain.name,
                                    onClick = { showBlockchainSelectorDialog = true }
                                )
                            }
                            add {
                                MenuItem(
                                    title = stringResource(Res.string.ExtendedKey_Account),
                                    value = viewModel.account.toString(),
                                    infoButtonClick = {
                                        navigation.slideFromBottom(KeyAccountInfoPage())
                                    },
                                    onClick = { showAccountSelectorDialog = true }
                                )
                            }
                        }
                    }

                    if (menuItems.isNotEmpty()) {
                        CellUniversalLawrenceSection(menuItems)
                    }

                    VSpacer(32.dp)
                    if (viewModel.displayKeyType.isPrivate) {
                        HidableContent(
                            viewModel.extendedKey,
                            stringResource(Res.string.ExtendedKey_TapToShowPrivateKey)
                        ) {
                        }
                    } else {
                        HidableContent(viewModel.extendedKey)
                    }

                    if (showPurposeSelectorDialog) {
                        AppSelectorDialog(
                            title = stringResource(Res.string.ExtendedKey_Purpose),
                            items = viewModel.purposes.map {
                                AppSelectorItem(it.name, it == viewModel.purpose, it)
                            },
                            onDismiss = {
                                showPurposeSelectorDialog = false
                            },
                            onSelect = {
                                viewModel.set(it)
                            }
                        )
                    }
                    if (showBlockchainSelectorDialog) {
                        AppSelectorDialog(
                            title = stringResource(Res.string.ExtendedKey_Blockchain),
                            items = viewModel.blockchains.map {
                                AppSelectorItem(it.name, it == viewModel.blockchain, it)
                            },
                            onDismiss = {
                                showBlockchainSelectorDialog = false
                            },
                            onSelect = {
                                viewModel.set(it)
                            }
                        )
                    }
                    if (showAccountSelectorDialog) {
                        AppSelectorDialog(
                            title = stringResource(Res.string.ExtendedKey_Account),
                            items = viewModel.accounts.map {
                                AppSelectorItem(it.toString(), it == viewModel.account, it)
                            },
                            onDismiss = {
                                showAccountSelectorDialog = false
                            },
                            onSelect = {
                                viewModel.set(it)
                            }
                        )
                    }
                }

                ActionButton(Res.string.Alert_Copy) {
                    if (viewModel.displayKeyType.isPrivate) {
                        coroutineScope.launch {
                            sheetState.show()
                        }
                    } else {
                        copyWithHud(viewModel.extendedKey)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(
    title: String,
    value: String,
    infoButtonClick: (() -> Unit)? = null,
    onClick: (() -> Unit)?
) {
    RowUniversal(
        modifier = Modifier.padding(horizontal = 16.dp),
        onClick = onClick
    ) {
        body_leah(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        infoButtonClick?.let { click ->
            HSpacer(width = 8.dp)
            HsIconButton(
                modifier = Modifier.size(20.dp),
                onClick = click
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_info_20),
                    contentDescription = "info button",
                    tint = ComposeAppTheme.colors.iconSecondary
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(
            Modifier
                .fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            subhead1_grey(
                text = value,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
            if (onClick != null) {
                Icon(
                    modifier = Modifier.padding(start = 4.dp),
                    painter = painterResource(Res.drawable.ic_down_arrow_20),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.iconSecondary
                )
            }
        }
    }
}

@Composable
private fun NoExtendKeyScreen() {

}
