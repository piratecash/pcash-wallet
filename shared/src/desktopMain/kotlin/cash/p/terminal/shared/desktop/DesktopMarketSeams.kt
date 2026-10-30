package cash.p.terminal.shared.desktop

import cash.p.terminal.modules.market.MarketPages
import cash.p.terminal.navigation.HSPage

class DesktopMarketPages : MarketPages {
    override fun markdown(url: String): HSPage? = null
    override fun subscriptionInfo(): HSPage? = null
}
