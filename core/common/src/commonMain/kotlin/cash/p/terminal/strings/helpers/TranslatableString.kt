package cash.p.terminal.strings.helpers

import androidx.compose.runtime.Composable
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.formatWithArgs
import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

sealed class TranslatableString {
    class PlainString(val text: String) : TranslatableString()
    class ResString(val id: StringResource, vararg val formatArgs: Any) : TranslatableString()
    class ResArrayString(val array: StringArrayResource, val index: Int) : TranslatableString()

    @Composable
    fun getString(): String {
        return when (val s = this) {
            is PlainString -> s.text
            is ResString -> stringResource(s.id).formatWithArgs(s.formatArgs)
            is ResArrayString -> stringArrayResource(s.array)[s.index]
        }
    }

    // The spread keeps toString routed through the public Translator, which tests mock.
    @Suppress("SpreadOperator")
    override fun toString(): String {
        return when (val s = this) {
            is PlainString -> s.text
            is ResString -> Translator.getString(s.id, *s.formatArgs)
            is ResArrayString -> Translator.getStringArrayItem(s.array, s.index)
        }
    }
}

interface WithTranslatableTitle {
    val title: TranslatableString
}
