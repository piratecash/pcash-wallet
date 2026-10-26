package cash.p.terminal.core

import cash.p.terminal.wallet.entities.TokenType

val TokenType.isNative: Boolean
    get() = this is TokenType.Native ||
            this == TokenType.Mweb ||
            this is TokenType.Derived ||
            this is TokenType.AddressTyped ||
            this is TokenType.AddressSpecTyped
