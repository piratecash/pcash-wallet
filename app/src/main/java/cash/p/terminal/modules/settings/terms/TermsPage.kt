package cash.p.terminal.modules.settings.terms

import android.os.Parcelable
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.p.terminal.R
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.modules.evmfee.ButtonsGroupWithShade
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.ui_compose.components.HsCheckbox
import cash.p.terminal.ui_compose.components.AppBar
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.MenuItem
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.parcelize.Parcelize

class TermsPage : HSPage() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        TermsScreen(page = this, navigation = navigation)
    }

    @Parcelize
    data class Result(val termsAccepted: Boolean) : Parcelable
}

@Composable
private fun TermsScreen(
    page: TermsPage,
    navigation: HSNavigation,
    viewModel: TermsViewModel = viewModel(factory = TermsModule.Factory())
) {
    BackHandler {
        navigation.setResult(page, TermsPage.Result(false))
        navigation.navigateUpSafely()
    }

    if (viewModel.closeWithTermsAgreed) {
        viewModel.closedWithTermsAgreed()

        navigation.setResult(page, TermsPage.Result(true))
        navigation.navigateUpSafely()
    }

    Scaffold(
        containerColor = ComposeAppTheme.colors.tyler,
        topBar = {
            AppBar(
                title = stringResource(R.string.Settings_Terms),
                menuItems = listOf(
                    MenuItem(
                        title = TranslatableString.ResString(R.string.Button_Close),
                        icon = R.drawable.ic_close_24,
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
                        title = stringResource(R.string.Button_IAgree),
                        onClick = { viewModel.onAgreeClick() },
                        enabled = viewModel.buttonEnabled
                    )
                }
            }
        }
    }
}

