package cash.p.terminal.ui_compose.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TitleValueCell(title: String, value: String) {
    RowUniversal(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
    ) {
        subhead2_grey(text = title)
        Spacer(Modifier.weight(1f))
        subhead1_leah(text = value)
    }
}
