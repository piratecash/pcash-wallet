package cash.p.terminal.core

import io.horizontalsystems.ethereumkit.core.toRawHexString
import retrofit2.HttpException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

fun Throwable.isNoInternetException(): Boolean {
    return generateSequence(this) { it.cause }
        .any { throwable ->
            throwable is UnknownHostException ||
                throwable is NoRouteToHostException ||
                throwable is UnresolvedAddressException ||
                throwable.message.equals("No internet connection", ignoreCase = true)
        }
}

fun Throwable.isBlockedByProvider(): Boolean =
    generateSequence(this) { it.cause }.any { (it as? HttpException)?.code() == 403 }

fun ByteArray?.to0xHexString(): String {
    val rawHex = this?.toRawHexString() ?: return ""
    return "0x$rawHex"
}

inline fun <reified T : Enum<T>> Enum.Companion.valueOrDefault(index: Int, default: T): T {
    return enumValues<T>().getOrNull(index) ?: default
}

fun String.splitToAddresses(): List<String> {
    return split(",")
        .map { it.split("\n") }.flatten()
        .map { it.replace(Regex(":\\d+"), "") }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}
