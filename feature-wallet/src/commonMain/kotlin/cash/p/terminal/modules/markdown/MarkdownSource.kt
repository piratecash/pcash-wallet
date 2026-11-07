package cash.p.terminal.modules.markdown

interface MarkdownSource {
    suspend fun load(url: String): String
}
