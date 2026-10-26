package cash.p.terminal.modules.multiswap.providers.backendswap

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_changelly
import cash.p.terminal.resources.ic_swap_24
import org.jetbrains.compose.resources.DrawableResource

/** Local branding for p.cash backend providers, keyed by the backend provider name. */
object BackendSwapBranding {
    private class Brand(val title: String, val icon: DrawableResource)

    private val brands = mapOf(
        "changelly" to Brand(title = "Changelly", icon = Res.drawable.ic_changelly),
    )

    fun title(providerName: String): String =
        brands[providerName]?.title ?: providerName.replaceFirstChar { it.titlecase() }

    fun icon(providerName: String): DrawableResource = brands[providerName]?.icon ?: Res.drawable.ic_swap_24
}
