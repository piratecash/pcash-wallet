package cash.p.terminal.core.managers

import androidx.annotation.StringRes
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import cash.p.terminal.BuildConfig
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.entities.Faq
import cash.p.terminal.entities.FaqMap
import cash.p.terminal.modules.main.MainActivity
import cash.p.terminal.modules.markdown.localreader.MarkdownLocalPage
import cash.p.terminal.navigation.HSNavigation
import io.horizontalsystems.core.BackgroundManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import timber.log.Timber
import java.lang.reflect.Type
import java.net.URL

object FaqManager {

    private val faqListUrl = AppConfigProvider.faqUrl

    const val faqMigrationRequired = "faq_migration_required_"
    const val faqMigrationRecommended = "faq_migration_recommended_"
    const val faqPrivateKeys = "faq_private_keys_"

    private val gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd")
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .registerTypeAdapter(Faq::class.java, FaqDeserializer(faqListUrl))
        .create()

    fun showFaqPage(assetPrefix: String) {
        navigateToMarkdown(MarkdownLocalPage.Input.Asset(assetPrefix, showAsPopup = true))
    }

    fun showFaqPage(@StringRes resId: Int) {
        navigateToMarkdown(MarkdownLocalPage.Input.Resource(resId, showAsPopup = true))
    }

    private fun navigateToMarkdown(input: MarkdownLocalPage.Input) {
        val navigation = rootNavigation() ?: run {
            val error = IllegalStateException("FaqManager: root navigation unavailable")
            check(!BuildConfig.DEBUG) { error.message.orEmpty() }
            Timber.e(error)
            return
        }
        navigation.slideFromBottom(MarkdownLocalPage(input))
    }

    private fun rootNavigation(): HSNavigation? =
        (getKoinInstance<BackgroundManager>().currentActivity as? MainActivity)?.navigation

    suspend fun getFaqList(): List<FaqMap> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(faqListUrl)
            .build()

        val listType = object : TypeToken<List<FaqMap>>() {}.type
        APIClient.okHttpClient.newCall(request).execute().use { response ->
            gson.fromJson(response.body?.charStream(), listType)
        }
    }

    class FaqDeserializer(faqUrl: String) : JsonDeserializer<Faq> {
        private val faqUrlObj = URL(faqUrl)

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Faq {
            val jsonObject = json.asJsonObject

            return Faq(
                jsonObject["title"].asString,
                absolutify(jsonObject["markdown"].asString)
            )
        }

        private fun absolutify(relativeUrl: String?): String {
            return URL(faqUrlObj, relativeUrl).toString()
        }
    }
}
