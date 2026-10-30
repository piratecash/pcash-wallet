package cash.p.terminal.entities

import androidx.annotation.Keep
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Keep
@Serializable
data class AppVersion(
    val version: String,
    val timestamp: Long
)

private val appVersionsSerializer = ListSerializer(AppVersion.serializer())

fun List<AppVersion>.encodeToJson(): String = Json.encodeToString(appVersionsSerializer, this)

/** Throws on a malformed [this]. */
fun String.decodeAppVersions(): List<AppVersion> = Json.decodeFromString(appVersionsSerializer, this)
