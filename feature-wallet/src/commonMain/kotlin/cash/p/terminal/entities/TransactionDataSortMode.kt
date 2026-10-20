package cash.p.terminal.entities

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.SettingsSecurity_SortingBip69
import cash.p.terminal.resources.SettingsSecurity_SortingBip69Description
import cash.p.terminal.resources.SettingsSecurity_SortingBip69Short
import cash.p.terminal.resources.SettingsSecurity_SortingShuffle
import cash.p.terminal.resources.SettingsSecurity_SortingShuffleDescription
import org.jetbrains.compose.resources.StringResource

enum class TransactionDataSortMode(val raw: String) {
    Shuffle("shuffle"),
    Bip69("bip69");

    val title: StringResource
        get() = when (this) {
            Shuffle -> Res.string.SettingsSecurity_SortingShuffle
            Bip69 -> Res.string.SettingsSecurity_SortingBip69
        }

    val titleShort: StringResource
        get() = when (this) {
            Shuffle -> Res.string.SettingsSecurity_SortingShuffle
            Bip69 -> Res.string.SettingsSecurity_SortingBip69Short
        }

    val description: StringResource
        get() = when (this) {
            Shuffle -> Res.string.SettingsSecurity_SortingShuffleDescription
            Bip69 -> Res.string.SettingsSecurity_SortingBip69Description
        }

}
