package cash.p.terminal.modules.managewallets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cash.p.terminal.feature.wallet.WalletPlatformPages
import cash.p.terminal.modules.addtoken.AddTokenPage
import cash.p.terminal.modules.configuredtoken.ConfiguredTokenInfoBottomSheet
import cash.p.terminal.modules.enablecoin.restoresettings.IRestoreSettingsUi
import cash.p.terminal.modules.enablecoin.restoresettings.openRestoreSettingsDialog
import cash.p.terminal.modules.restoreaccount.restoreblockchains.CoinViewItem
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.resources.ManageCoins_AddToken
import cash.p.terminal.resources.ManageCoins_NoResults
import cash.p.terminal.resources.ManageCoins_title
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.entries_count
import cash.p.terminal.resources.ic_add_yellow
import cash.p.terminal.resources.ic_arrow_big_down_20
import cash.p.terminal.resources.ic_info_20
import cash.p.terminal.resources.ic_not_found
import cash.p.terminal.resources.ic_offline_16
import cash.p.terminal.resources.options_count
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui.compose.components.ListEmptyView
import cash.p.terminal.ui_compose.awaitImeHidden
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryDefaults
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.HsBackButton
import cash.p.terminal.ui_compose.components.HsIconButton
import cash.p.terminal.ui_compose.components.HsSwitch
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.SearchField
import cash.p.terminal.ui_compose.components.SnackbarDuration
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.rememberHudMessenger
import cash.p.terminal.ui_compose.components.body_leah
import cash.p.terminal.ui_compose.components.subhead2_grey
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.wallet.Token
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun ManageWalletsScreen(
    navigation: HSNavigation,
    manageWalletsCallback: ManageWalletsCallback,
    onBackPressed: () -> Unit,
    requestScan: () -> Unit,
    restoreSettingsViewModel: IRestoreSettingsUi
) {
    val groupsList by manageWalletsCallback.groupsList.collectAsStateWithLifecycle()
    val hud = rememberHudMessenger()
    val mainPage = koinInject<WalletPlatformPages>().mainPage
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    var initialLoading by remember { mutableStateOf(true) }
    var infoToken by remember { mutableStateOf<Token?>(null) }

    LaunchedEffect(groupsList) {
        if (groupsList.isNotEmpty()) {
            initialLoading = false
        }
    }

    navigation.openRestoreSettingsDialog(
        token = restoreSettingsViewModel.openTokenConfigure,
        restoreSettingsViewModel = restoreSettingsViewModel
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.background(color = ComposeAppTheme.colors.backgroundBase)
        ) {
            AppBar(
                title = stringResource(Res.string.ManageCoins_title),
                navigationIcon = {
                    HsBackButton(onClick = onBackPressed)
                },
                menuItems = if (manageWalletsCallback.addTokenEnabled) {
                    listOf(
                        MenuItem(
                            title = TranslatableString.ResString(Res.string.ManageCoins_AddToken),
                            icon = Res.drawable.ic_add_yellow,
                            onClick = {
                                navigation.slideFromRightForResult<AddTokenPage.Result>(
                                    AddTokenPage()
                                ) { result ->
                                    val tokenToEnable = result.tokenToEnable
                                    if (tokenToEnable != null) {
                                        // Hardware wallet: enqueue the token for the scan pipeline
                                        // (shows "Scan card to add") and stay on this screen.
                                        manageWalletsCallback.enable(tokenToEnable)
                                    } else if (result.success) {
                                        navigation.removeLastUntil(mainPage, false)
                                    }
                                }
                            }
                        ))
                } else {
                    listOf()
                }
            )

            SearchField(
                onSearchTextChanged = manageWalletsCallback::updateFilter,
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 12.dp
                ),
            )

            if (groupsList.isEmpty()) {
                if (initialLoading) {
                    LoadingComponent()
                } else {
                    Column {
                        ListEmptyView(
                            text = stringResource(Res.string.ManageCoins_NoResults),
                            icon = Res.drawable.ic_not_found,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            } else {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = ComposeAppTheme.colors.borderDivider,
                )
                LazyColumn(Modifier.weight(1f)) {
                    items(
                        items = groupsList,
                        key = { it.coinUid }
                    ) { group ->
                        CoinGroupItem(
                            group = group,
                            onGroupClick = {
                                manageWalletsCallback.toggleGroupExpansion(group.coinUid)
                            },
                            onItemClick = { token ->
                                if (group.items.find { it.item == token }?.enabled == true) {
                                    manageWalletsCallback.disable(token)
                                } else {
                                    manageWalletsCallback.enable(token)
                                }
                            },
                            onInfoClick = { token ->
                                coroutineScope.launch {
                                    focusManager.clearFocus()
                                    imeInsets.awaitImeHidden(density)
                                    infoToken = token
                                }
                            }
                        )
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = ComposeAppTheme.colors.borderDivider,
                        )
                    }
                    item {
                        VSpacer(height = 32.dp)
                    }
                    if (manageWalletsCallback.showScanToAddButton) {
                        item {
                            VSpacer(height = ButtonPrimaryDefaults.MinHeight + 32.dp)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = manageWalletsCallback.showScanToAddButton,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            ScanToAddBlock(manageWalletsCallback.hardwareActionButtonText, requestScan)
        }
        LaunchedEffect(manageWalletsCallback.errorMsg) {
            manageWalletsCallback.errorMsg?.let {
                hud.showError(it)
            }
        }

        LaunchedEffect(manageWalletsCallback.offlineNoticeMsg) {
            manageWalletsCallback.offlineNoticeMsg?.let {
                hud.showMessage(text = it, icon = Res.drawable.ic_offline_16, duration = SnackbarDuration.LONG)
                manageWalletsCallback.onOfflineNoticeShown()
            }
        }

        LaunchedEffect(manageWalletsCallback.closeScreen) {
            if (manageWalletsCallback.closeScreen) {
                navigation.navigateUp()
            }
        }
        val totalItems = remember(groupsList) { groupsList.sumOf { it.items.size } }
        TotalItemsPanel(
            count = totalItems,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
        )
    }

    infoToken?.let { token ->
        ConfiguredTokenInfoBottomSheet(
            token = token,
            onDismiss = { infoToken = null }
        )
    }
}

@Composable
private fun TotalItemsPanel(count: Int, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .background(ComposeAppTheme.colors.surfacePrimary)
    ) {
        Text(
            text = pluralStringResource(
                Res.plurals.entries_count,
                count,
                count
            ),
            style = ComposeAppTheme.typography.caption,
            color = ComposeAppTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun LoadingComponent() {
    Box(Modifier.fillMaxSize()) {
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.Center)
                .size(56.dp)
                .padding(top = 4.dp, end = 8.dp),
            color = ComposeAppTheme.colors.textSecondary,
            strokeWidth = 4.dp
        )
    }
}

@Composable
private fun CoinGroupItem(
    group: CoinGroup,
    onGroupClick: () -> Unit,
    onItemClick: (Token) -> Unit,
    onInfoClick: (Token) -> Unit
) {
    if (group.isSingleOption) {
        val item = group.items.first()
        TokenRow(
            viewItem = item,
            startPadding = 16.dp,
            iconSize = 32.dp,
            onItemClick = {
                onItemClick(item.item)
            },
            onInfoClick = {
                onInfoClick(item.item)
            }
        )
    } else {
        Column {
            GroupHeader(
                group = group,
                onClick = onGroupClick
            )

            AnimatedVisibility(
                visible = group.isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    group.items.forEach { viewItem ->
                        TokenRow(
                            viewItem = viewItem,
                            startPadding = 32.dp,
                            iconSize = 24.dp,
                            onItemClick = {
                                onItemClick(viewItem.item)
                            },
                            onInfoClick = {
                                onInfoClick(viewItem.item)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(
    group: CoinGroup,
    onClick: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (group.isExpanded) 180f else 0f,
        label = "arrow_rotation"
    )

    RowUniversal(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalPadding = 0.dp
    ) {
        val firstItem = group.items.firstOrNull()
        firstItem?.let {
            Image(
                painter = it.imageSource.painter(),
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 16.dp, top = 12.dp, bottom = 12.dp)
                    .size(32.dp)
                    .clip(CircleShape)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp)
        ) {
            body_leah(
                text = group.coinName,
                maxLines = 1,
            )
            subhead2_grey(
                text = pluralStringResource(
                    Res.plurals.options_count,
                    group.items.size,
                    group.items.size
                ),
                maxLines = 1,
                modifier = Modifier.padding(top = 1.dp)
            )
        }

        Icon(
            painter = painterResource(Res.drawable.ic_arrow_big_down_20),
            contentDescription = null,
            tint = ComposeAppTheme.colors.iconSecondary,
            modifier = Modifier
                .padding(end = 2.dp)
                .rotate(rotation)
        )
    }
}

@Composable
private fun ScanToAddBlock(buttonText: String, requestScan: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        color = ComposeAppTheme.colors.backgroundBase,
    ) {
        ButtonPrimaryYellow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp + with(LocalDensity.current) {
                        NavigationBarDefaults.windowInsets
                            .getBottom(LocalDensity.current)
                            .toDp()
                    },
                    top = 16.dp
                ),
            title = buttonText,
            onClick = requestScan
        )
    }
}

@Composable
private fun TokenRow(
    viewItem: CoinViewItem<Token>,
    startPadding: Dp,
    iconSize: Dp,
    onItemClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    RowUniversal(
        onClick = onItemClick,
        modifier = Modifier.padding(start = startPadding, end = 16.dp),
        verticalPadding = 0.dp
    ) {
        Image(
            painter = viewItem.imageSource.painter(),
            contentDescription = null,
            modifier = Modifier
                .padding(end = 16.dp, top = 12.dp, bottom = 12.dp)
                .size(iconSize)
                .clip(CircleShape)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                body_leah(
                    text = viewItem.title,
                    maxLines = 1,
                )
                viewItem.label?.let { labelText ->
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(ComposeAppTheme.colors.badgeBackground)
                    ) {
                        Text(
                            modifier = Modifier.padding(
                                start = 4.dp,
                                end = 4.dp,
                                bottom = 1.dp
                            ),
                            text = labelText,
                            color = ComposeAppTheme.colors.textPrimary,
                            style = ComposeAppTheme.typography.microSB,
                            maxLines = 1,
                        )
                    }
                }
            }
            subhead2_grey(
                text = viewItem.subtitle,
                maxLines = 1,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        if (viewItem.hasInfo) {
            HsIconButton(onClick = onInfoClick) {
                Icon(
                    painter = painterResource(Res.drawable.ic_info_20),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.iconSecondary
                )
            }
        }
        HsSwitch(
            modifier = Modifier.padding(0.dp),
            checked = viewItem.enabled,
            onCheckedChange = { onItemClick.invoke() },
        )
    }
}
