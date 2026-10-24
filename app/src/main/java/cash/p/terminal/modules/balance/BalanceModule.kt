package cash.p.terminal.modules.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cash.p.terminal.core.App
import cash.p.terminal.core.getKoinInstance
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.modules.address.AddressHandlerFactory
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.Tron_TokenPage_AddressNotActive_Info
import cash.p.terminal.resources.Tron_TokenPage_AddressNotActive_Title
import cash.p.terminal.strings.helpers.TranslatableString
import cash.p.terminal.wallet.balance.BalanceWarning
import cash.p.terminal.wallet.managers.IBalanceHiddenManager

object BalanceModule {
    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val totalService = TotalService(
                currencyManager = getKoinInstance(),
                marketKit = getKoinInstance(),
                baseTokenManager = getKoinInstance(),
                balanceHiddenManager = getKoinInstance()
            )
            return BalanceViewModel(
                service = DefaultBalanceService.getInstance("wallet"),
                balanceViewItemFactory = BalanceViewItemFactory(getKoinInstance()),
                balanceViewTypeManager = getKoinInstance(),
                totalBalance = TotalBalance(totalService, getKoinInstance()),
                localStorage = getKoinInstance(),
                addressHandlerFactory = AddressHandlerFactory(AppConfigProvider.udnApiKey),
                priceManager = App.priceManager,
                balanceHiddenManager = getKoinInstance<IBalanceHiddenManager>(),
            ) as T
        }
    }

    val BalanceWarning.warningText: WarningText
        get() = when (this) {
            BalanceWarning.TronInactiveAccountWarning -> WarningText(
                title = TranslatableString.ResString(Res.string.Tron_TokenPage_AddressNotActive_Title),
                text = TranslatableString.ResString(Res.string.Tron_TokenPage_AddressNotActive_Info),
            )
        }
}
