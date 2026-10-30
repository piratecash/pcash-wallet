package cash.p.terminal.modules.send.address

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import cash.p.terminal.navigation.HSNavigation
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import cash.p.terminal.core.address.ContractAddressChecker
import cash.p.terminal.core.rememberPremiumAction
import cash.p.terminal.ui_compose.components.SectionUniversalLawrence
import cash.p.terminal.ui_compose.components.SwitchWithText
import cash.p.terminal.wallet.Token
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.settings_smart_contract_check

private val contractAddressChecker by lazy(LazyThreadSafetyMode.PUBLICATION) {
    ContractAddressChecker()
}

fun isSmartContractCheckSupported(token: Token): Boolean =
    contractAddressChecker.supports(token)

@Composable
fun SmartContractCheckSection(
    token: Token,
    navigation: HSNavigation,
    addressCheckerControl: AddressCheckerControl,
    modifier: Modifier = Modifier
) {
    val setSmartContractCheck = navigation.rememberPremiumAction<Boolean> {
        addressCheckerControl.onCheckSmartContractAddressClick(it)
    }
    val isSupported = remember(token.blockchainType, token.type) {
        isSmartContractCheckSupported(token)
    }

    if (!isSupported) return

    SectionUniversalLawrence(modifier = modifier) {
        SwitchWithText(
            text = stringResource(Res.string.settings_smart_contract_check),
            checked = addressCheckerControl.uiState.addressCheckSmartContractEnabled,
            onCheckedChange = setSmartContractCheck
        )
    }
}
