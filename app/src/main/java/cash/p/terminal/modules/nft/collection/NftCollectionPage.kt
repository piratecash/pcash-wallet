package cash.p.terminal.modules.nft.collection

import android.os.Parcelable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.modules.nft.collection.assets.NftCollectionAssetsScreen
import cash.p.terminal.modules.nft.collection.events.NftCollectionEventsScreen
import cash.p.terminal.modules.nft.collection.overview.NftCollectionOverviewScreen
import cash.p.terminal.modules.nft.collection.overview.NftCollectionOverviewViewModel
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.TabItem
import cash.p.terminal.ui_compose.components.Tabs
import cash.p.terminal.ui.helpers.LinkHelper
import cash.p.terminal.ui.helpers.TextHelper
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.ui_compose.components.HudHelper
import io.horizontalsystems.core.entities.BlockchainType
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class NftCollectionPage(val input: Input) : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val blockchainType = BlockchainType.fromUid(input.blockchainTypeUid)

        val viewModel = viewModel<NftCollectionOverviewViewModel>(
            factory = NftCollectionModule.Factory(blockchainType, input.collectionUid)
        )

        NftCollectionScreen(
            navigation,
            viewModel
        )
    }

    @Parcelize
    data class Input(val collectionUid: String, val blockchainTypeUid: String) : Parcelable
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NftCollectionScreen(navigation: HSNavigation, viewModel: NftCollectionOverviewViewModel) {
    val tabs = viewModel.tabs
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current
    val context = LocalContext.current

    Column(modifier = Modifier.background(color = ComposeAppTheme.colors.tyler)) {
        AppBar(
            menuItems = listOf(
                MenuItem(
                    title = TranslatableString.ResString(R.string.Button_Close),
                    icon = R.drawable.ic_close_24,
                    onClick = {
                        navigation.navigateUpSafely()
                    }
                )
            )
        )

        val selectedTab = tabs[pagerState.currentPage]
        val tabItems = tabs.map {
            TabItem(stringResource(id = it.titleResId), it == selectedTab, it)
        }
        Tabs(tabItems, onClick = {
            coroutineScope.launch {
                pagerState.scrollToPage(it.ordinal)
            }
        })

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false
        ) { page ->
            when (tabs[page]) {
                NftCollectionModule.Tab.Overview -> {
                    NftCollectionOverviewScreen(
                        viewModel,
                        onCopyText = {
                            TextHelper.copyText(it)
                            HudHelper.showSuccessMessage(view, R.string.Hud_Text_Copied)
                        },
                        onOpenUrl = {
                            LinkHelper.openLinkInAppBrowser(context, it)
                        }
                    )
                }

                NftCollectionModule.Tab.Items -> {
                    NftCollectionAssetsScreen(navigation, viewModel.blockchainType, viewModel.collectionUid)
                }

                NftCollectionModule.Tab.Activity -> {
                    NftCollectionEventsScreen(
                        navigation, viewModel.blockchainType, viewModel.collectionUid, viewModel.contracts
                    )
                }
            }
        }
    }
}
