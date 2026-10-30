package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_warning_filled_24
import cash.p.terminal.resources.radar_24
import cash.p.terminal.resources.shield_check_filled_24
import cash.p.terminal.resources.swap_provider_type_auto
import cash.p.terminal.resources.swap_provider_type_auto_description
import cash.p.terminal.resources.swap_provider_type_controlled
import cash.p.terminal.resources.swap_provider_type_controlled_description
import cash.p.terminal.resources.swap_provider_type_flexible
import cash.p.terminal.resources.swap_provider_type_flexible_description
import cash.p.terminal.resources.swap_provider_type_precheck
import cash.p.terminal.resources.swap_provider_type_precheck_description
import cash.p.terminal.resources.thumbsup_24
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

enum class ProviderRiskType(
    val titleRes: StringResource,
    val descriptionRes: StringResource,
    val iconRes: DrawableResource,
) {
    Auto(
        Res.string.swap_provider_type_auto,
        Res.string.swap_provider_type_auto_description,
        Res.drawable.shield_check_filled_24,
    ),
    Flexible(
        Res.string.swap_provider_type_flexible,
        Res.string.swap_provider_type_flexible_description,
        Res.drawable.thumbsup_24,
    ),
    PreCheck(
        Res.string.swap_provider_type_precheck,
        Res.string.swap_provider_type_precheck_description,
        Res.drawable.radar_24,
    ),
    Controlled(
        Res.string.swap_provider_type_controlled,
        Res.string.swap_provider_type_controlled_description,
        Res.drawable.ic_warning_filled_24,
    ),
}
