package cash.p.terminal.modules.send.address

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import cash.p.terminal.core.address.AddressCheckType
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.TransparentModalBottomSheet
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.VSpacer
import cash.p.terminal.ui_compose.components.highlightText
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Button_Close
import cash.p.terminal.resources.Send_Address_AmlCheck_Info1
import cash.p.terminal.resources.Send_Address_AmlCheck_Info2
import cash.p.terminal.resources.Send_Address_AmlCheck_Info3
import cash.p.terminal.resources.Send_Address_BlacklistCheck
import cash.p.terminal.resources.Send_Address_BlacklistCheck_Info1
import cash.p.terminal.resources.Send_Address_BlacklistCheck_Info2
import cash.p.terminal.resources.Send_Address_BlacklistCheck_Info3
import cash.p.terminal.resources.Send_Address_Error_Clear
import cash.p.terminal.resources.Send_Address_Error_Detected
import cash.p.terminal.resources.Send_Address_NotSmartContractCheck
import cash.p.terminal.resources.Send_Address_NotSmartContractCheck_Info1
import cash.p.terminal.resources.Send_Address_NotSmartContractCheck_Info2
import cash.p.terminal.resources.Send_Address_NotSmartContractCheck_Info3
import cash.p.terminal.resources.Send_Address_PhishingCheck
import cash.p.terminal.resources.Send_Address_PhishingCheck_Info1
import cash.p.terminal.resources.Send_Address_PhishingCheck_Info2
import cash.p.terminal.resources.Send_Address_PhishingCheck_Info3
import cash.p.terminal.resources.Send_Address_SanctionCheck
import cash.p.terminal.resources.Send_Address_SanctionCheck_Info1
import cash.p.terminal.resources.Send_Address_SanctionCheck_Info2
import cash.p.terminal.resources.Send_Address_SanctionCheck_Info3
import cash.p.terminal.resources.check_from_aml
import cash.p.terminal.resources.ic_info_24

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressEnterInfoBottomSheet(
    checkType: AddressCheckType,
    hideBottomSheet: () -> Unit,
    bottomSheetState: SheetState
) {
    val title = when (checkType) {
        AddressCheckType.SmartContract -> Res.string.Send_Address_NotSmartContractCheck
        AddressCheckType.Phishing -> Res.string.Send_Address_PhishingCheck
        AddressCheckType.Blacklist -> Res.string.Send_Address_BlacklistCheck
        AddressCheckType.AmlCheck -> Res.string.check_from_aml
        AddressCheckType.Sanction -> Res.string.Send_Address_SanctionCheck
    }

    TransparentModalBottomSheet(
        onDismissRequest = hideBottomSheet,
        sheetState = bottomSheetState,
    ) {
        BottomSheetHeader(
            iconPainter = painterResource(Res.drawable.ic_info_24),
            iconTint = ColorFilter.tint(ComposeAppTheme.colors.iconSecondary),
            title = stringResource(title),
            titleColor = ComposeAppTheme.colors.textPrimary,
            onCloseClick = hideBottomSheet
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = 12.dp, horizontal = 24.dp)
                    .fillMaxWidth()
            ) {
                InfoBlock(checkType)
                VSpacer(36.dp)
                ButtonPrimaryYellow(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(Res.string.Button_Close),
                    onClick = hideBottomSheet
                )
                VSpacer(32.dp)
            }
        }
    }
}

@Composable
fun InfoBlock(checkType: AddressCheckType) {
    val info1 = when (checkType) {
        AddressCheckType.SmartContract -> AnnotatedString(
            stringResource(Res.string.Send_Address_NotSmartContractCheck_Info1)
        )

        AddressCheckType.Phishing -> AnnotatedString(stringResource(Res.string.Send_Address_PhishingCheck_Info1))

        AddressCheckType.Blacklist -> highlightText(
            text = stringResource(Res.string.Send_Address_BlacklistCheck_Info1),
            textColor = ComposeAppTheme.colors.textPrimary,
            highlightPart = "Hashdit.io",
            highlightColor = ComposeAppTheme.colors.brandDefault
        )

        AddressCheckType.AmlCheck -> highlightText(
            text = stringResource(Res.string.Send_Address_AmlCheck_Info1),
            textColor = ComposeAppTheme.colors.textPrimary,
            highlightPart = " alpha-aml.com",
            highlightColor = ComposeAppTheme.colors.brandDefault
        )

        AddressCheckType.Sanction -> highlightText(
            text = stringResource(Res.string.Send_Address_SanctionCheck_Info1),
            textColor = ComposeAppTheme.colors.textPrimary,
            highlightPart = "Chainalysis.com",
            highlightColor = ComposeAppTheme.colors.brandDefault
        )
    }

    val info2 = when (checkType) {
        AddressCheckType.SmartContract -> highlightText(
            text = stringResource(Res.string.Send_Address_NotSmartContractCheck_Info2),
            textColor = ComposeAppTheme.colors.textPrimary,
            highlightPart = stringResource(Res.string.Send_Address_Error_Clear),
            highlightColor = ComposeAppTheme.colors.statusSuccess
        )

        AddressCheckType.Phishing -> highlightText(
            text = stringResource(Res.string.Send_Address_PhishingCheck_Info2),
            textColor = ComposeAppTheme.colors.textPrimary,
            highlightPart = stringResource(Res.string.Send_Address_Error_Clear),
            highlightColor = ComposeAppTheme.colors.statusSuccess
        )

        AddressCheckType.Blacklist -> highlightText(
            text = stringResource(Res.string.Send_Address_BlacklistCheck_Info2),
            textColor = ComposeAppTheme.colors.textPrimary,
            highlightPart = stringResource(Res.string.Send_Address_Error_Clear),
            highlightColor = ComposeAppTheme.colors.statusSuccess
        )

        AddressCheckType.AmlCheck -> highlightText(
            text = stringResource(Res.string.Send_Address_AmlCheck_Info2),
            textColor = ComposeAppTheme.colors.textPrimary,
            highlightPart = stringResource(Res.string.Send_Address_Error_Clear),
            highlightColor = ComposeAppTheme.colors.statusSuccess
        )

        AddressCheckType.Sanction -> highlightText(
            text = stringResource(Res.string.Send_Address_SanctionCheck_Info2),
            textColor = ComposeAppTheme.colors.textPrimary,
            highlightPart = stringResource(Res.string.Send_Address_Error_Clear),
            highlightColor = ComposeAppTheme.colors.statusSuccess
        )
    }

    val info3 = when (checkType) {
        AddressCheckType.SmartContract -> highlightText(
            text = stringResource(Res.string.Send_Address_NotSmartContractCheck_Info3),
            textColor = ComposeAppTheme.colors.textPrimary,
            highlightPart = stringResource(Res.string.Send_Address_Error_Detected),
            highlightColor = ComposeAppTheme.colors.statusError
        )

        AddressCheckType.Phishing -> highlightText(
            text = stringResource(Res.string.Send_Address_PhishingCheck_Info3),
            textColor = ComposeAppTheme.colors.textPrimary,
            highlightPart = stringResource(Res.string.Send_Address_Error_Detected),
            highlightColor = ComposeAppTheme.colors.statusError
        )

        AddressCheckType.Blacklist -> highlightText(
            text = stringResource(Res.string.Send_Address_BlacklistCheck_Info3),
            textColor = ComposeAppTheme.colors.textPrimary,
            highlightPart = stringResource(Res.string.Send_Address_Error_Detected),
            highlightColor = ComposeAppTheme.colors.statusError
        )

        AddressCheckType.AmlCheck -> highlightText(
            text = stringResource(Res.string.Send_Address_AmlCheck_Info3),
            textColor = ComposeAppTheme.colors.textPrimary,
            highlightPart = stringResource(Res.string.Send_Address_Error_Detected),
            highlightColor = ComposeAppTheme.colors.statusError
        )

        AddressCheckType.Sanction -> highlightText(
            text = stringResource(Res.string.Send_Address_SanctionCheck_Info3),
            textColor = ComposeAppTheme.colors.textPrimary,
            highlightPart = stringResource(Res.string.Send_Address_Error_Detected),
            highlightColor = ComposeAppTheme.colors.statusError
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = info1,
            style = ComposeAppTheme.typography.body,
            color = ComposeAppTheme.colors.textPrimary,
        )
        VSpacer(24.dp)
        Text(
            text = info2,
            style = ComposeAppTheme.typography.body,
            color = ComposeAppTheme.colors.textPrimary,
        )
        VSpacer(8.dp)
        Text(
            text = info3,
            style = ComposeAppTheme.typography.body,
            color = ComposeAppTheme.colors.textPrimary,
        )
    }
}
