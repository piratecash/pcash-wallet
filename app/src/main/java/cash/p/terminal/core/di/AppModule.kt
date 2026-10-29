package cash.p.terminal.core.di

import cash.p.terminal.di.managerModule
import cash.p.terminal.feature.market.di.marketFeatureModule
import cash.p.terminal.feature.wallet.di.featureWalletModule
import cash.p.terminal.di.storageModule
import cash.p.terminal.di.swapProvidersModule
import cash.p.terminal.di.viewModelModule
import cash.p.terminal.feature.logging.di.featureLoggingModule
import cash.p.terminal.ledger.di.featureLedgerModule
import cash.p.terminal.ledger.di.ledgerUsbModule
import cash.p.terminal.modules.hardwarewallet.ledgerConnectorModule
import cash.p.terminal.feature.miniapp.di.featureMiniAppModule
import cash.p.terminal.featureStacking.di.featureStackingModule
import cash.p.terminal.network.di.networkModule
import cash.p.terminal.premium.di.featurePremiumModule
import cash.p.terminal.tangem.di.featureTangemModule
import cash.p.terminal.trezor.di.featureTrezorModule
import cash.p.terminal.wallet.di.walletFeatureModule
import org.koin.dsl.module

val appModule = module {
    includes(
        storageModule,
        managerModule,
        marketFeatureModule,
        viewModelModule,
        walletFeatureModule,
        featureWalletModule,
        featureStackingModule,
        featureTangemModule,
        featureTrezorModule,
        featureLedgerModule,
        ledgerUsbModule,
        ledgerConnectorModule,
        networkModule,
        swapProvidersModule,
        useCaseModule,
        appUpdateModule,
        featurePremiumModule,
        featureLoggingModule,
        featureMiniAppModule
    )
}
