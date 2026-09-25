package io.horizontalsystems.core.ui.dialogs

import android.os.Parcelable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cash.p.terminal.navigation.HSBottomSheet
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.navigateUpSafely
import cash.p.terminal.ui_compose.BottomSheetHeader
import cash.p.terminal.ui_compose.R
import cash.p.terminal.ui_compose.components.ButtonPrimaryYellow
import cash.p.terminal.ui_compose.components.CellUniversalLawrenceSection
import cash.p.terminal.ui_compose.components.HsCheckbox
import cash.p.terminal.ui_compose.components.RowUniversal
import cash.p.terminal.ui_compose.components.subhead2_leah
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import kotlinx.parcelize.Parcelize

class ChecklistConfirmationSheet(val input: ChecklistConfirmationInput) : HSBottomSheet() {

    @Composable
    override fun GetContent(navigation: HSNavigation) {
        ChecklistConfirmationScreen(
            input = input,
            onClose = navigation::navigateUpSafely,
            onConfirm = {
                navigation.setResult(this, Result(true))
                navigation.navigateUpSafely()
            }
        )
    }

    @Parcelize
    data class ChecklistConfirmationInput(
        val items: List<String>,
        val title: String,
        val confirmButtonText: String
    ) : Parcelable

    @Parcelize
    data class Result(val confirmed: Boolean) : Parcelable
}

@Composable
private fun ChecklistConfirmationScreen(
    input: ChecklistConfirmationSheet.ChecklistConfirmationInput,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
) {
    val checkedItems = remember { mutableStateMapOf<Int, Boolean>() }

    LaunchedEffect(input.items) {
        input.items.forEachIndexed { index, _ ->
            checkedItems[index] = false
        }
    }

    val allItemsChecked = checkedItems.values.all { it }

    BottomSheetHeader(
        iconPainter = painterResource(R.drawable.ic_attention_red_24),
        title = input.title,
        onCloseClick = onClose
    ) {
        Spacer(Modifier.height(12.dp))

        CellUniversalLawrenceSection(
            items = input.items.mapIndexed { index, item ->
                ChecklistItem(index, item, checkedItems[index] ?: false)
            },
            showFrame = true
        ) { item ->
            RowUniversal(
                modifier = Modifier.padding(horizontal = 16.dp),
                onClick = {
                    checkedItems[item.index] = !checkedItems[item.index]!!
                }
            ) {
                HsCheckbox(
                    checked = item.checked,
                    onCheckedChange = {
                        checkedItems[item.index] = it
                    },
                )
                Spacer(Modifier.width(16.dp))
                subhead2_leah(
                    text = item.text,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        ButtonPrimaryYellow(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
            title = input.confirmButtonText,
            onClick = onConfirm,
            enabled = allItemsChecked
        )
        Spacer(Modifier.height(32.dp))
    }
}

private data class ChecklistItem(
    val index: Int,
    val text: String,
    val checked: Boolean
)

@Preview(showBackground = true)
@Composable
private fun ChecklistConfirmationScreenPreview() {
    ComposeAppTheme {
        ChecklistConfirmationScreen(
            input = ChecklistConfirmationSheet.ChecklistConfirmationInput(
                items = listOf(
                    "Condition 1",
                    "Condition 2",
                    "Condition 3"
                ),
                title = "Title",
                confirmButtonText = "Confirm"
            ),
            onClose = {},
            onConfirm = {}
        )
    }
}
