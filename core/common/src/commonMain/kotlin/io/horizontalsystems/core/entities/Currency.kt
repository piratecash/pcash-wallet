package io.horizontalsystems.core.entities

import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize

@CommonParcelize
data class Currency(
    val code: String,
    val symbol: String,
    val decimal: Int,
    val flag: Int
) : CommonParcelable
