package cash.p.terminal.core.deeplink

import android.net.Uri
import cash.p.terminal.BuildConfig
import cash.p.terminal.core.ICoinManager
import cash.p.terminal.feature.miniapp.domain.storage.IUniqueCodeStorage
import cash.p.terminal.feature.miniapp.ui.connect.ConnectMiniAppDeeplinkInput
import cash.p.terminal.feature.miniapp.ui.connect.ConnectMiniAppPage
import cash.p.terminal.modules.main.DeeplinkPage
import cash.p.terminal.modules.multiswap.SwapPage
import cash.p.terminal.modules.premium.about.AboutPremiumPage
import cash.p.terminal.wallet.entities.TokenQuery

/**
 * Parses pcash:// deeplinks for swap and premium screens.
 * Used by both QRScannerPage (for scanned QR codes) and MainViewModel (for external deeplinks).
 */
class DeeplinkParser(
    private val coinManager: ICoinManager,
    private val uniqueCodeStorage: IUniqueCodeStorage
) {
    fun parse(uri: Uri): DeeplinkPage? {
        if (uri.scheme != "pcash") {
            return null
        }

        return when (uri.host) {
            "premium" -> {
                DeeplinkPage(AboutPremiumPage(null), fromBottom = false)
            }

            "swap" -> {
                val toTokenParam = uri.getQueryParameter("to_token")
                val tokenQuery = when (toTokenParam?.uppercase()) {
                    "PIRATE" -> TokenQuery.PirateCashBnb
                    "COSA" -> TokenQuery.CosantaBnb
                    else -> null
                }
                val token = tokenQuery?.let { coinManager.getToken(it) }
                DeeplinkPage(SwapPage(tokenOut = token), fromBottom = false)
            }

            "auth" -> parseAuth(uri)

            else -> null
        }
    }

    private fun parseAuth(uri: Uri): DeeplinkPage? {
        val jwt = uri.getQueryParameter("token") ?: return null
        val endpoint = when (uri.getQueryParameter("env")) {
            "stage" -> "https://anubis.pirate.place/"
            "dev" -> "https://cash.p.cash/"
            else -> "https://p.cash/"
        }
        // Test-only: seeds a SWAP 5 code the backend would otherwise have to issue.
        if (BuildConfig.DEBUG) {
            uri.getQueryParameter("code")?.let { uniqueCodeStorage.uniqueCode = it }
        }
        return DeeplinkPage(
            ConnectMiniAppPage(
                ConnectMiniAppDeeplinkInput(
                    jwt = jwt,
                    endpoint = endpoint
                )
            ),
            fromBottom = true
        )
    }

    fun parse(text: String): DeeplinkPage? {
        return try {
            parse(Uri.parse(text))
        } catch (e: Exception) {
            null
        }
    }
}
