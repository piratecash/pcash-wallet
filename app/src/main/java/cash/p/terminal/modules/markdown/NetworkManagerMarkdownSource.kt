package cash.p.terminal.modules.markdown

import cash.p.terminal.core.INetworkManager
import java.net.URL

class NetworkManagerMarkdownSource(private val networkManager: INetworkManager) : MarkdownSource {
    override suspend fun load(url: String): String {
        val parsed = URL(url)
        return networkManager.getMarkdown("${parsed.protocol}://${parsed.host}", url)
    }
}
