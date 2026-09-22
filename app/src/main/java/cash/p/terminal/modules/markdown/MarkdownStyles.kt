package cash.p.terminal.modules.markdown

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cash.p.terminal.ui_compose.theme.Colors
import com.halilibo.richtext.ui.HeadingStyle

fun defaultHeadingStyle(colors: Colors): HeadingStyle {
    return { level, textStyle ->
        when (level) {
            0 -> TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            1 -> TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            2 -> TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.brand)
            3 -> TextStyle(fontSize = 16.sp, color = colors.brand)
            4 -> TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.brand)
            5 -> TextStyle(fontWeight = FontWeight.Bold, color = colors.brand)
            else -> textStyle
        }
    }
}
