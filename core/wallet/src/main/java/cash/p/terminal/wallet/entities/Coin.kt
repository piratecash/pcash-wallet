package cash.p.terminal.wallet.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Coin(
    val uid: String,
    val name: String,
    val code: String,
    val marketCapRank: Int? = null,
    val image: String? = null,
    val priority: Int? = null
) : Parcelable {
    override fun equals(other: Any?): Boolean {
        return other is Coin && other.uid == uid
    }

    override fun hashCode(): Int {
        return uid.hashCode()
    }

    override fun toString(): String {
        return "Coin [uid: $uid; name: $name; code: $code; marketCapRank: $marketCapRank; " +
                "priority: $priority]"
    }
}
