package cash.p.terminal.modules.balance

import cash.p.terminal.core.providers.AddressResolutionConfig
import cash.p.terminal.modules.address.AddressHandlerFactory
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Tron_TokenPage_AddressNotActive_Info
import cash.p.terminal.resources.Tron_TokenPage_AddressNotActive_Title
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.balance.BalanceWarning
import org.koin.core.scope.Scope

object BalanceModule {
    val BalanceWarning.warningText: WarningText
        get() = when (this) {
            BalanceWarning.TronInactiveAccountWarning -> WarningText(
                title = TranslatableString.ResString(Res.string.Tron_TokenPage_AddressNotActive_Title),
                text = TranslatableString.ResString(Res.string.Tron_TokenPage_AddressNotActive_Info),
            )
        }
}

fun Scope.balanceViewModel() = BalanceViewModel(
    service = DefaultBalanceService.getInstance("wallet"),
    balanceViewItemFactory = get(),
    balanceViewTypeManager = get(),
    totalBalance = get(),
    localStorage = get(),
    platformActions = get(),
    addressHandlerFactory = AddressHandlerFactory(get<AddressResolutionConfig>().udnApiKey),
    priceManager = get(),
    balanceHiddenManager = get(),
)
