package cash.p.terminal.ui.helpers

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import androidx.core.net.toUri
import cash.p.terminal.R
import cash.p.terminal.modules.markdown.validUrlOrNull
import cash.p.terminal.ui_compose.theme.appColors

object LinkHelper {

    fun openLinkInAppBrowser(context: Context, link: String) {
        val urlString = validUrlOrNull(link) ?: return

        try {
            val builder = CustomTabsIntent.Builder()

            val color = context.appColors().backgroundBase.toArgb()

            val params = CustomTabColorSchemeParams.Builder()
                .setNavigationBarColor(color)
                .setToolbarColor(color)
                .build()

            builder.setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_DARK, params)
            builder.setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_LIGHT, params)
            builder.setStartAnimations(context, R.anim.slide_from_right, R.anim.slide_to_left)
            builder.setExitAnimations(
                context,
                android.R.anim.slide_in_left,
                android.R.anim.slide_out_right
            )

            val intent = builder.build()
            intent.launchUrl(context, urlString.toUri())
        } catch (e: Exception) {
            // Fallback to standard intent if Custom Tabs fails
            try {
                val intent = Intent(Intent.ACTION_VIEW, urlString.toUri())
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("LinkHelper", "Failed to open URL: $urlString", e)
            }
        }
    }
}
