package cash.p.terminal.di

import cash.p.terminal.modules.multiswap.TimerService
import cash.p.terminal.modules.multiswap.exchange.MultiSwapExchangeViewModel
import cash.p.terminal.modules.multiswap.exchanges.MultiSwapExchangesViewModel
import cash.p.terminal.modules.multiswap.providersettings.SwapProvidersSettingsViewModel
import cash.p.terminal.modules.hardwarewallet.HardwareWalletViewModel
import cash.p.terminal.modules.main.MainActivityViewModel
import cash.p.terminal.modules.main.MainViewModel
import cash.p.terminal.modules.softwareupdate.SoftwareUpdateViewModel
import cash.p.terminal.modules.softwareupdate.changelog.VersionChangelogViewModel
import cash.p.terminal.modules.softwareupdate.history.VersionHistoryViewModel
import cash.p.terminal.modules.pin.unlock.PinUnlockViewModel
import cash.p.terminal.modules.markdown.localreader.MarkdownLocalViewModel
import cash.p.terminal.modules.premium.settings.PushNotificationsViewModel
import cash.p.terminal.modules.premium.smsnotification.SendSmsNotificationViewModel
import cash.p.terminal.modules.qrscanner.QRScannerViewModel
import cash.p.terminal.modules.qrscanner.QrCodeImageDecoder
import cash.p.terminal.modules.resettofactorysettings.ResetToFactorySettingsViewModel
import cash.p.terminal.modules.send.offline.OfflineBroadcastViewModel
import cash.p.terminal.modules.calculator.autolock.CalculatorAutoLockViewModel
import cash.p.terminal.modules.calculator.lockscreen.CalculatorLockScreenViewModel
import cash.p.terminal.modules.calculator.pinsettings.CalculatorPinSettingsViewModel
import cash.p.terminal.modules.settings.advancedsecurity.AdvancedSecurityViewModel
import cash.p.terminal.modules.settings.advancedsecurity.terms.DeleteContactsTermsViewModel
import cash.p.terminal.modules.settings.advancedsecurity.securereset.SecureResetTermsViewModel
import cash.p.terminal.modules.settings.advancedsecurity.terms.HiddenWalletTermsViewModel
import cash.p.terminal.modules.settings.displaytransactions.DisplayTransactionsViewModel
import cash.p.terminal.modules.settings.guides.GuidesViewModel
import cash.p.terminal.modules.subscription.ActivateSubscriptionViewModel
import cash.p.terminal.modules.walletconnect.request.WCRequestEvmViewModel
import cash.p.terminal.modules.settings.main.MainSettingsViewModel
import cash.p.terminal.modules.settings.privacy.PrivacyViewModel
import cash.p.terminal.modules.settings.security.passcode.SecuritySettingsViewModel
import cash.p.terminal.modules.tonconnect.TonConnectListViewModel
import cash.p.terminal.modules.walletconnect.AccountTypeNotSupportedSheet
import cash.p.terminal.modules.walletconnect.AccountTypeNotSupportedViewModel
import cash.p.terminal.modules.zcashmigration.ZcashMigrationViewModel
import cash.p.terminal.modules.multiswap.SwapSelectCoinViewModel
import cash.p.terminal.modules.xrate.XRateService
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.DefaultDispatcherProvider
import io.horizontalsystems.core.DispatcherProvider
import cash.p.terminal.modules.pin.SetDuressPinIntroViewModel
import cash.p.terminal.modules.settings.appearance.AppIconViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val viewModelModule = module {
    singleOf(::DefaultDispatcherProvider) bind DispatcherProvider::class
    singleOf(::QrCodeImageDecoder)

    viewModelOf(::MainViewModel)
    viewModelOf(::MainActivityViewModel)
    viewModelOf(::SoftwareUpdateViewModel)
    viewModelOf(::VersionHistoryViewModel)
    viewModel { params -> VersionChangelogViewModel(params.get(), get(), get()) }
    viewModelOf(::DisplayTransactionsViewModel)
    viewModelOf(::OfflineBroadcastViewModel)
    viewModelOf(::GuidesViewModel)
    viewModelOf(::ActivateSubscriptionViewModel)
    viewModelOf(::WCRequestEvmViewModel)
    viewModelOf(::PrivacyViewModel)
    viewModelOf(::SetDuressPinIntroViewModel)
    viewModelOf(::AppIconViewModel)
    viewModelOf(::HardwareWalletViewModel)
    viewModelOf(::ResetToFactorySettingsViewModel)
    viewModelOf(::SecuritySettingsViewModel)
    viewModelOf(::MainSettingsViewModel)
    viewModelOf(::MarkdownLocalViewModel)
    viewModelOf(::PushNotificationsViewModel)
    viewModelOf(::SendSmsNotificationViewModel)
    viewModelOf(::TonConnectListViewModel)
    viewModelOf(::QRScannerViewModel)
    viewModelOf(::PinUnlockViewModel)
    viewModel { (input: AccountTypeNotSupportedSheet.Input) ->
        AccountTypeNotSupportedViewModel(input = input, accountManager = get())
    }
    viewModelOf(::AdvancedSecurityViewModel)
    viewModelOf(::CalculatorPinSettingsViewModel)
    viewModelOf(::CalculatorAutoLockViewModel)
    viewModel { CalculatorLockScreenViewModel(get(), get(), get()) }
    viewModelOf(::DeleteContactsTermsViewModel)
    viewModelOf(::HiddenWalletTermsViewModel)
    viewModelOf(::SecureResetTermsViewModel)
    viewModelOf(::MultiSwapExchangesViewModel)
    viewModelOf(::SwapProvidersSettingsViewModel)
    viewModel { params ->
        MultiSwapExchangeViewModel(
            pendingMultiSwapId = params.get(),
            pendingMultiSwapStorage = get(),
            marketKit = get(),
            numberFormatter = get(),
            onChainMonitor = get(),
            swapQuoteService = get(),
            swapProvidersRepository = get(),
            fetchSwapQuotesUseCase = get(),
            timerService = TimerService(),
            syncPendingMultiSwapUseCase = get(),
            assetFiatRateService = get(),
            currencyManager = get(),
            adapterManager = get(),
            balanceHiddenManager = get(),
            walletManager = get(),
            walletUseCase = get(),
            accountManager = get(),
        )
    }
    viewModel { (otherSelectedToken: Token?, activeAccount: Account) ->
        SwapSelectCoinViewModel(
            otherSelectedToken = otherSelectedToken,
            activeAccount = activeAccount,
            dispatcherProvider = get()
        )
    }
    viewModel { (wallet: Wallet) ->
        ZcashMigrationViewModel(
            wallet = wallet,
            pendingRegistrar = get(),
            numberFormatter = get(),
            adapterManager = get(),
            xRateService = XRateService(get(), get<CurrencyManager>().baseCurrency)
        )
    }
}
