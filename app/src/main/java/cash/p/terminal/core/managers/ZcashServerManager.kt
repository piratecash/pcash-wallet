package cash.p.terminal.core.managers

import cash.p.terminal.core.storage.BlockchainSettingsStorage
import cash.p.terminal.core.tryOrNull
import cash.p.zcash.ServerConfig
import cash.p.zcash.Transport
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.net.URI

data class ZcashServer(val name: String, val url: String, val isCustom: Boolean)

object ZcashServerUrl {

    sealed interface ParseResult {
        data class Valid(val normalized: String) : ParseResult
        data object Invalid : ParseResult
    }

    private const val ONION_SUFFIX = ".onion"
    private val DEFAULT_PORTS = mapOf("https" to 443, "http" to 80)

    fun parse(raw: String): ParseResult {
        val uri = tryOrNull { URI(raw.trim()) } ?: return ParseResult.Invalid
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        val port = if (uri.port == -1) DEFAULT_PORTS[scheme] else uri.port
        val valid = scheme in DEFAULT_PORTS &&
            !host.isNullOrBlank() &&
            port in 1..65535 &&
            uri.rawUserInfo == null &&
            uri.rawQuery == null &&
            uri.rawFragment == null &&
            uri.rawPath.orEmpty() in listOf("", "/") &&
            (scheme == "https" || isOnion(host))
        return if (valid) ParseResult.Valid("$scheme://$host:$port") else ParseResult.Invalid
    }

    fun isOnion(host: String?): Boolean =
        host != null && host.length > ONION_SUFFIX.length && host.endsWith(ONION_SUFFIX)
}

sealed interface AddResult {
    data class Added(val server: ZcashServer) : AddResult
    data object Duplicate : AddResult
    data object Invalid : AddResult
}

class ZcashServerManager(private val storage: BlockchainSettingsStorage) {

    // DROP_OLDEST: collectors re-read current state, and a slow one must not make tryEmit fail for the rest.
    private val _serverSelectedFlow = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val serverSelectedFlow: SharedFlow<Unit> = _serverSelectedFlow.asSharedFlow()

    private val _serversUpdatedFlow = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val serversUpdatedFlow: SharedFlow<Unit> = _serversUpdatedFlow.asSharedFlow()

    val defaultServers: List<ZcashServer> = listOf(
        defaultServer("zec.rocks (global)", "zec.rocks"),
        defaultServer("zec.rocks (NA)", "na.zec.rocks"),
        defaultServer("zec.rocks (SA)", "sa.zec.rocks"),
        defaultServer("zec.rocks (EU)", "eu.zec.rocks"),
        defaultServer("zec.rocks (AP)", "ap.zec.rocks"),
    )

    val customServers: List<ZcashServer>
        get() = storage.zcashCustomServers().map { ZcashServer(it.substringAfter("://"), it, isCustom = true) }

    val allServers: List<ZcashServer>
        get() = defaultServers + customServers

    val isAuto: Boolean
        get() = storage.zcashServerAuto()

    val current: ZcashServer
        get() {
            val preferred = if (isAuto) storage.zcashAutoServerUrl() else storage.zcashServerUrl()
            return allServers.firstOrNull { it.url == preferred } ?: defaultServers.first()
        }

    fun autoPool(torEnabled: Boolean): List<ZcashServer> =
        allServers.filter { torEnabled || !ZcashServerUrl.isOnion(tryOrNull { URI(it.url).host }) }

    fun selectAuto() = emitSelectedIfChanged { storage.saveZcashServerAuto(true) }

    fun select(url: String) = emitSelectedIfChanged {
        storage.saveZcashServerAuto(false)
        storage.saveZcashServerUrl(url)
    }

    fun applyAutoChoice(url: String) {
        if (!isAuto || allServers.none { it.url == url }) return
        emitSelectedIfChanged { storage.saveZcashAutoServerUrl(url) }
    }

    private inline fun emitSelectedIfChanged(change: () -> Unit) {
        val before = isAuto to current.url
        change()
        if (before != isAuto to current.url) _serverSelectedFlow.tryEmit(Unit)
    }

    fun addCustom(raw: String): AddResult {
        val url = (ZcashServerUrl.parse(raw) as? ZcashServerUrl.ParseResult.Valid)?.normalized
            ?: return AddResult.Invalid
        if (allServers.any { it.url == url }) return AddResult.Duplicate

        storage.saveZcashCustomServers(storage.zcashCustomServers() + url)
        _serversUpdatedFlow.tryEmit(Unit)
        select(url)
        return AddResult.Added(ZcashServer(url.substringAfter("://"), url, isCustom = true))
    }

    fun delete(url: String) {
        val custom = storage.zcashCustomServers()
        if (url !in custom) return
        val wasCurrent = current.url == url

        storage.saveZcashCustomServers(custom - url)
        _serversUpdatedFlow.tryEmit(Unit)
        if (!wasCurrent) return
        if (isAuto) storage.saveZcashAutoServerUrl("") else storage.saveZcashServerUrl(defaultServers.first().url)
        _serverSelectedFlow.tryEmit(Unit)
    }

    fun serverConfig(torEnabled: Boolean): ServerConfig =
        current.url.let { ServerConfig(url = it, transport = transportFor(it, torEnabled)) }

    fun transportFor(url: String, torEnabled: Boolean): Transport =
        if (torEnabled || ZcashServerUrl.isOnion(tryOrNull { URI(url).host })) Transport.TOR else Transport.DIRECT

    private fun defaultServer(name: String, host: String) = ZcashServer(name, "https://$host:443", isCustom = false)
}
