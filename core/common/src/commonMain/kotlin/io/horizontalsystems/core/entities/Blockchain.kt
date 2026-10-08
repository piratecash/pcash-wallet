package io.horizontalsystems.core.entities

import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize
import java.util.Objects

@CommonParcelize
data class Blockchain(
    val type: BlockchainType,
    val name: String,
    val eip3091url: String?
) : CommonParcelable {

    val uid: String
        get() = type.uid

    override fun equals(other: Any?): Boolean =
        other is Blockchain && other.type == type

    override fun hashCode(): Int =
        Objects.hash(type, name)

}
