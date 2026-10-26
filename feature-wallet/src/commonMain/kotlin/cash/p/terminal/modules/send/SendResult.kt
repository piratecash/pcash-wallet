package cash.p.terminal.modules.send

import cash.p.terminal.core.HSCaution

sealed class SendResult {
    data object Sending : SendResult()
    data class Sent(val recordUid: String? = null) : SendResult()
    data class SentButQueued(val recordUid: String? = null) : SendResult()
    class Failed(val caution: HSCaution) : SendResult()
}
