package cash.p.terminal.modules.multiswap.providers.backendswap

import androidx.annotation.DrawableRes
import cash.p.terminal.R

/** Local branding for p.cash backend providers, keyed by the backend provider name. */
object BackendSwapBranding {
    private class Brand(val title: String, @DrawableRes val icon: Int)

    private val brands = mapOf(
        "changelly" to Brand(title = "Changelly", icon = R.drawable.ic_changelly),
    )

    fun title(providerName: String): String =
        brands[providerName]?.title ?: providerName.replaceFirstChar { it.titlecase() }

    @DrawableRes
    fun icon(providerName: String): Int = brands[providerName]?.icon ?: R.drawable.ic_swap_24
}
