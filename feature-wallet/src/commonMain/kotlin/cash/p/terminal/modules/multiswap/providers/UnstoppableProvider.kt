package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_exolix
import cash.p.terminal.resources.ic_quickex
import cash.p.terminal.resources.ic_unstoppable_barter
import cash.p.terminal.resources.ic_unstoppable_cce
import cash.p.terminal.resources.ic_unstoppable_circle
import cash.p.terminal.resources.ic_unstoppable_letsexchange
import cash.p.terminal.resources.ic_unstoppable_near
import cash.p.terminal.resources.ic_unstoppable_pegasus
import cash.p.terminal.resources.ic_unstoppable_stealthex
import cash.p.terminal.resources.ic_unstoppable_swapuz
import org.jetbrains.compose.resources.DrawableResource

/**
 * Unstoppable v2 aggregator sub-providers this app routes through. [apiId] is the exact string
 * the v2 API expects (`providers` set on `/v2/rate`, `provider` field on `/v2/swap`, `provider`
 * query param on `/v2/tokens`) — do not rename without checking the API contract.
 *
 * [riskType] is derived from the provider's own type/compliance posture (DEX-style routing vs.
 * CEX with AML pre-check vs. CEX with AML review vs. CEX with neither), matching the convention
 * already used by [ExolixProvider]/[QuickexProvider]/[ChangeNowProvider] — it is not a mechanical
 * copy of Unstoppable's own risk classification.
 *
 * [isEvm] selects the wrapper: EVM sub-providers commit a signed transaction the wallet broadcasts
 * ([UnstoppableEvmSwapProvider]); the rest deposit to an address ([UnstoppableSwapProvider]).
 */
enum class UnstoppableProvider(
    val apiId: String,
    val title: String,
    val icon: DrawableResource,
    val riskType: ProviderRiskType,
    val isEvm: Boolean,
) {
    Near(
        apiId = "NEAR",
        title = "Near",
        icon = Res.drawable.ic_unstoppable_near,
        riskType = ProviderRiskType.Auto,
        isEvm = false,
    ),
    QuickEx(
        apiId = "QUICKEX",
        title = "QuickEx",
        icon = Res.drawable.ic_quickex,
        riskType = ProviderRiskType.PreCheck,
        isEvm = false,
    ),
    LetsExchange(
        apiId = "LETSEXCHANGE",
        title = "LetsExchange",
        icon = Res.drawable.ic_unstoppable_letsexchange,
        riskType = ProviderRiskType.Controlled,
        isEvm = false,
    ),
    StealthEx(
        apiId = "STEALTHEX",
        title = "StealthEX",
        icon = Res.drawable.ic_unstoppable_stealthex,
        riskType = ProviderRiskType.Controlled,
        isEvm = false,
    ),
    Exolix(
        apiId = "EXOLIX",
        title = "Exolix",
        icon = Res.drawable.ic_exolix,
        riskType = ProviderRiskType.Controlled,
        isEvm = false,
    ),
    Cce(
        apiId = "CCE",
        title = "CCE Cash",
        icon = Res.drawable.ic_unstoppable_cce,
        riskType = ProviderRiskType.Controlled,
        isEvm = false,
    ),
    Swapuz(
        apiId = "SWAPUZ",
        title = "Swapuz",
        icon = Res.drawable.ic_unstoppable_swapuz,
        riskType = ProviderRiskType.Flexible,
        isEvm = false,
    ),
    Barter(
        apiId = "BARTER",
        title = "Barter",
        icon = Res.drawable.ic_unstoppable_barter,
        riskType = ProviderRiskType.Auto,
        isEvm = true,
    ),
    Circle(
        apiId = "CIRCLE",
        title = "Circle CCTP",
        icon = Res.drawable.ic_unstoppable_circle,
        riskType = ProviderRiskType.Auto,
        isEvm = true,
    ),
    Pegasus(
        apiId = "PEGASUS",
        title = "PegasusSwap",
        icon = Res.drawable.ic_unstoppable_pegasus,
        riskType = ProviderRiskType.Controlled,
        isEvm = false,
    );

    val id: String get() = "u_" + apiId.lowercase()

    companion object {
        /**
         * QuickEx and Exolix are excluded because this app already integrates them directly via
         * [QuickexProvider]/[ExolixProvider] — routing them a second time through Unstoppable
         * would duplicate the same liquidity under a different provider id. LetsExchange is
         * routed through [YiFiProvider] instead.
         */
        val EXCLUDED: Set<UnstoppableProvider> = setOf(QuickEx, Exolix, LetsExchange)

        fun registrable(): List<UnstoppableProvider> = entries.filterNot { it in EXCLUDED }

        /**
         * Display title for a stored sub-provider [apiId] (from
         * [cash.p.terminal.entities.SwapProviderTransaction.subProviderId]). Falls back to
         * the raw id if the sub-provider was later removed from this enum.
         */
        fun displayTitle(apiId: String?): String? =
            apiId?.let { id -> entries.firstOrNull { it.apiId == id }?.title ?: id }
    }
}
