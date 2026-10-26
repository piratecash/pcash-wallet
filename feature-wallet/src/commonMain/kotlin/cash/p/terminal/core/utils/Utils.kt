package cash.p.terminal.core.utils

import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

object Utils {

    suspend fun waitUntil(timeout: Long, checkPeriod: Long, condition: () -> Boolean) {
        var waited = 0L
        while (!condition.invoke() && waited < timeout) {
            delay(checkPeriod)
            waited += checkPeriod
        }
    }

    fun getIpByUrl(host: String): List<String>? = try {
        InetAddress
            .getAllByName(host)
            .filter { it !is Inet6Address }
            .mapNotNull { it.hostAddress }
    } catch (e: UnknownHostException) {
        Logger.withTag("Utils").d { "getIpByUrl: $host not found" }
        null
    }
}

object EthInputParser {

    fun parse(input: String): InputData? {

        val transferIndex = input.indexOf("a9059cbb")
        if (transferIndex > -1) {
            val startIndex = transferIndex + 8
            val startAddress = startIndex + 24
            val endAddress = startAddress + 40

            val address = input.substring(startAddress, endAddress)
            val amount = input.substring(endAddress, endAddress + 64)

            return InputData(from = null, to = address, value = amount)
        }

        val transferFromIndex = input.indexOf("23b872dd")
        if (transferFromIndex > -1) {
            val startIndex = transferFromIndex + 8

            val startFromAddress = startIndex + 24
            val endFromAddress = startFromAddress + 40

            val startToAddress = endFromAddress + 24
            val endToAddress = startToAddress + 40

            val from = input.substring(startFromAddress, endFromAddress)
            val to = input.substring(startToAddress, endToAddress)
            val amount = input.substring(endToAddress, endToAddress + 64)

            return InputData(from = from, to = to, value = amount)
        }

        return null
    }

    class InputData(var from: String?, var to: String, var value: String)

}
