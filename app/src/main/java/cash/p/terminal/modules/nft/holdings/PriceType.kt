package cash.p.terminal.modules.nft.holdings

import cash.p.terminal.resources.Nfts_PriceType_Days_30
import cash.p.terminal.resources.Nfts_PriceType_Days_7
import cash.p.terminal.resources.Nfts_PriceType_LastSale
import cash.p.terminal.resources.Res
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.strings.helpers.WithTranslatableTitle

enum class PriceType(override val title: TranslatableString) : WithTranslatableTitle {
    LastSale(TranslatableString.ResString(Res.string.Nfts_PriceType_LastSale)),
    Days7(TranslatableString.ResString(Res.string.Nfts_PriceType_Days_7)),
    Days30(TranslatableString.ResString(Res.string.Nfts_PriceType_Days_30))
}
