package cash.p.terminal.wallet.entities

import io.horizontalsystems.core.common.CommonParcelable
import androidx.room.Entity
import io.horizontalsystems.core.common.CommonParcelize

@CommonParcelize
data class SecretString(val value: String) : CommonParcelable {
    override fun toString(): String = "SecretString(***)"
}

@JvmInline
value class EncryptedString(val value: String)

data class SecretList(val list: List<String>) {
    override fun toString(): String = "SecretList(***)"
}

@Entity(primaryKeys = ["id"])
data class AccountRecord(
    val id: String,
    val name: String,
    val type: String,
    val origin: String,
    val isBackedUp: Boolean,
    val isFileBackedUp: Boolean,
    val words: SecretList?,
    val passphrase: SecretString?,
    val key: SecretString?,
    val level: Int
) {

    var deleted = false

    override fun equals(other: Any?): Boolean {
        if (other is AccountRecord) {
            return id == other.id
        }

        return false
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

}
