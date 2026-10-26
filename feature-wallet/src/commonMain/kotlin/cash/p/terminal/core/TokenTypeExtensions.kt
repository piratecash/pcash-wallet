package cash.p.terminal.core

import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.TonWalletVersion
import cash.p.terminal.wallet.entities.tokenQuery
import cash.p.terminal.wallet.entities.tokenType
import cash.p.terminal.wallet.entities.tonAsset
import cash.p.terminal.wallet.entities.withVersion

val TokenType.isNative: Boolean
    get() = this is TokenType.Native ||
            this == TokenType.TonW5Native ||
            this == TokenType.Mweb ||
            this is TokenType.Derived ||
            this is TokenType.AddressTyped ||
            this is TokenType.AddressSpecTyped

/** Outside the wallet a TON asset is one asset whatever the wallet version; its V4R2 variant names it. */
val TokenQuery.versionFreeTokenType: TokenType
    get() = tonAsset?.withVersion(TonWalletVersion.V4R2)?.tokenType ?: tokenType

val TokenQuery.paymentUriTokenUid: String?
    get() = when (tokenType) {
        is TokenType.Derived, is TokenType.AddressTyped -> null
        else -> versionFreeTokenType.id
    }

/** A TON asset exists once per wallet version; any other token only as itself. */
val TokenQuery.walletVersionQueries: List<TokenQuery>
    get() = tonAsset?.let { asset -> TonWalletVersion.entries.map { asset.withVersion(it).tokenQuery } }
        ?: listOf(this)
