package cash.p.terminal.wallet

import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType

val zcashAddressSpecTokenQueries: List<TokenQuery>
    get() = TokenType.AddressSpecType.entries.map {
        TokenQuery(BlockchainType.Zcash, TokenType.AddressSpecTyped(it))
    }

val zcashDefaultTokenQuery: TokenQuery
    get() = TokenQuery.ZcashUnified

val zcashLegacyNativeTokenQuery: TokenQuery
    get() = TokenQuery(BlockchainType.Zcash, TokenType.Native)

val zcashDisableTokenQueryIds: Set<String>
    get() = (zcashAddressSpecTokenQueries + zcashLegacyNativeTokenQuery)
        .mapTo(mutableSetOf()) { it.id }

val TokenQuery.isZcashAddressSpec: Boolean
    get() = blockchainType == BlockchainType.Zcash && tokenType is TokenType.AddressSpecTyped

val TokenQuery.isLegacyZcash: Boolean
    get() = blockchainType == BlockchainType.Zcash && tokenType == TokenType.Native

val Token.isZcashAddressSpec: Boolean
    get() = tokenQuery.isZcashAddressSpec

/** Only a seed owns the whole t/z/u group; an imported key owns just the pools it carries. */
val AccountType.ownsFullZcashGroup: Boolean
    get() = this is AccountType.Mnemonic

fun Collection<TokenQuery>.expandedZcashAddressSpecQueries(
    accountType: AccountType
): List<TokenQuery> {
    return replacingZcashQueries { accountType.ownsFullZcashGroup && it.isZcashAddressSpec }
}

fun Collection<TokenQuery>.normalizedZcashWalletQueriesForLoad(
    accountType: AccountType
): List<TokenQuery> {
    return replacingZcashQueries {
        it.isLegacyZcash || (accountType.ownsFullZcashGroup && it.isZcashAddressSpec)
    }
}

private fun Collection<TokenQuery>.replacingZcashQueries(
    shouldReplace: (TokenQuery) -> Boolean
): List<TokenQuery> {
    var zcashAdded = false

    return buildList {
        this@replacingZcashQueries.forEach { tokenQuery ->
            if (shouldReplace(tokenQuery)) {
                if (!zcashAdded) {
                    addAll(zcashAddressSpecTokenQueries)
                    zcashAdded = true
                }
            } else {
                add(tokenQuery)
            }
        }
    }.distinct()
}

fun Collection<Token>.expandedZcashAddressSpecTokens(
    marketKit: MarketKitWrapper,
    accountType: AccountType
): List<Token> {
    val requestedZcashTokens = filter { it.isZcashAddressSpec }

    if (requestedZcashTokens.isEmpty() || !accountType.ownsFullZcashGroup) {
        return distinctBy { it.tokenQuery.id }
    }

    val zcashTokens = (marketKit.tokens(zcashAddressSpecTokenQueries) + requestedZcashTokens)
        .distinctBy { it.tokenQuery.id }
    var zcashAdded = false

    return buildList {
        this@expandedZcashAddressSpecTokens.forEach { token ->
            if (token.isZcashAddressSpec) {
                if (!zcashAdded) {
                    addAll(zcashTokens)
                    zcashAdded = true
                }
            } else {
                add(token)
            }
        }
    }.distinctBy { it.tokenQuery.id }
}
