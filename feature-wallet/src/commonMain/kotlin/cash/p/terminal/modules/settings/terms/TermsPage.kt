package cash.p.terminal.modules.settings.terms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Button_IAgree
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Settings_Terms
import cash.p.terminal.resources.ic_close_24
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.HsCheckbox
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.ButtonsGroupWithShade
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

class TermsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        TermsScreen(page = this, navigation = navigation)
    }

    @CommonParcelize
    data class Result(val termsAccepted: Boolean) : CommonParcelable
}

@Composable
private fun TermsScreen(
    page: TermsPage,
    navigation: HSNavigation,
    viewModel: TermsViewModel = koinViewModel()
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        onBackCompleted = {
            navigation.setResult(page, TermsPage.Result(false))
            navigation.navigateUpSafely()
        },
    )

    if (viewModel.closeWithTermsAgreed) {
        viewModel.closedWithTermsAgreed()

        navigation.setResult(page, TermsPage.Result(true))
        navigation.navigateUpSafely()
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.backgroundBase,
        topBar = {
            AppBar(
                title = stringResource(Res.string.Settings_Terms),
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(Res.string.Button_Close),
                        icon = Res.drawable.ic_close_24,
                        onClick = {
                            navigation.setResult(page, TermsPage.Result(false))
                            navigation.navigateUpSafely()
                        }
                    )
                )
            )
        }
    ) {
        Column(Modifier.padding(it)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                CellUniversalLawrenceSection(viewModel.termsViewItems) { item ->
                    val onClick = if (!viewModel.readOnlyState) {
                        { viewModel.onTapTerm(item.termType, !item.checked) }
                    } else {
                        null
                    }

                    RowUniversal(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .testTag("terms_item"),
                        onClick = onClick
                    ) {
                        HsCheckbox(
                            checked = item.checked,
                            enabled = !viewModel.readOnlyState,
                            onCheckedChange = { checked ->
                                viewModel.onTapTerm(item.termType, checked)
                            },
                        )
                        Spacer(Modifier.width(16.dp))
                        subhead2_leah(
                            text = stringResource(item.termType.description)
                        )
                    }
                }

                Spacer(Modifier.height(60.dp))
            }

            if (viewModel.buttonVisible) {
                ButtonsGroupWithShade {
                    ButtonPrimaryYellow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        title = stringResource(Res.string.Button_IAgree),
                        onClick = { viewModel.onAgreeClick() },
                        enabled = viewModel.buttonEnabled
                    )
                }
            }
        }
    }
}

