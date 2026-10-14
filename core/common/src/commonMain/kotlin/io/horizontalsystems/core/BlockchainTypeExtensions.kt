package io.horizontalsystems.core

import io.horizontalsystems.core.entities.BlockchainType

val BlockchainType.imageUrl: String
    get() = "https://p.cash/storage/blockchains/$uid.png"
