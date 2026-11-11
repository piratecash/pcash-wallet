package cash.p.terminal.core.storage.desktop

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The only way to an account store: without it the store cannot be opened. */
@Entity
class AccountPointer(
    @PrimaryKey
    val accountId: String,
    val storageId: String,
    val storeKey: ByteArray,
)

@Entity
data class LevelSetting(
    @PrimaryKey
    val key: String,
    val value: String,
)

/** Where a safe lives and the key that opens it, as handed down from the safe above. */
@Serializable
class SafeLink(val slot: Int, val key: ByteArray, val envelopeIndex: Int)

/** Everything the safes being collapsed owned, so it can still be erased and returned once they are gone. */
@Serializable
data class CollapseIntent(
    val levels: List<SafeLink>,
    val slotPool: Set<Int>,
    val envelopePool: Set<Int>,
    /** The levels can no longer be entered, so nothing changes them and [levels] is final. */
    val envelopesFreed: Boolean = false,
)

/** A duress safe stores no mark of being one: from inside it reads exactly like a main root. */
@Entity
data class VaultSafeMeta(
    @PrimaryKey
    val id: Int = 0,
    val premiumKey: ByteArray,
    val slotPool: Set<Int>,
    val envelopePool: Set<Int>,
    val isHiddenRoot: Boolean,
    @Embedded(prefix = "child_")
    val child: SafeLink?,
    val collapse: CollapseIntent?,
)

@Entity
class AccountStoreMeta(
    @PrimaryKey
    val id: Int = 0,
    val kitKeys: Map<String, ByteArray>,
)

@Entity
data class AccountSetting(
    @PrimaryKey
    val key: String,
    val value: String,
)

class VaultConverters {
    @TypeConverter
    fun fromPool(pool: Set<Int>): String = Json.encodeToString(pool)

    @TypeConverter
    fun toPool(value: String): Set<Int> = Json.decodeFromString(value)

    @TypeConverter
    fun fromIntent(intent: CollapseIntent?): String? = intent?.let { Json.encodeToString(it) }

    @TypeConverter
    fun toIntent(value: String?): CollapseIntent? = value?.let { Json.decodeFromString(it) }

    @TypeConverter
    fun fromKitKeys(keys: Map<String, ByteArray>): String = Json.encodeToString(keys)

    @TypeConverter
    fun toKitKeys(value: String): Map<String, ByteArray> = Json.decodeFromString(value)
}
