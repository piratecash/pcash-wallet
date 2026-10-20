package cash.p.terminal.feature.wallet

interface PinStateInfo {
    val isPinSet: Boolean
    fun isDuressPinSet(): Boolean
    fun getDuressLevel(): Int
}
