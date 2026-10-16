package cash.p.terminal.ui.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_attention_red_20
import cash.p.terminal.ui_compose.components.ImageSource

@Preview(showBackground = true)
@Composable
private fun BottomSheetSelectorMultiplePreview() {
    val config = BottomSheetSelectorMultipleSheet.Config(
        title = "Select Options",
        description = "Please select the options you want.",
        descriptionTitle = "Options",
        viewItems = listOf(
            BottomSheetSelectorViewItem("Option 1", "Description 1", null, null),
            BottomSheetSelectorViewItem("Option 2", "Description 2", null, null),
            BottomSheetSelectorViewItem(
                "Option 3", "Description super long to test component and how it works", null, null
            )
        ),
        selectedIndexes = listOf(0, 2),
        icon = ImageSource.Local(Res.drawable.ic_attention_red_20),
        allowEmpty = true
    )

    BottomSheetSelectorMultiple(
        config = config,
        onItemsSelected = { /* Handle selection */ },
        onCloseClick = { /* Handle close */ }
    )
}
