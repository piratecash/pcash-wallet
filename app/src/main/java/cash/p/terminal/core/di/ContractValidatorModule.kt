package cash.p.terminal.core.di

import cash.p.terminal.core.address.AlphaAmlAddressValidator
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.modules.send.contractvalidators.ContractAddressValidator
import cash.p.terminal.modules.send.contractvalidators.EvmContractAddressValidator
import cash.p.terminal.modules.send.contractvalidators.ExcludedContractValidator
import cash.p.terminal.modules.send.contractvalidators.SolanaContractAddressValidator
import cash.p.terminal.modules.send.contractvalidators.TonContractAddressValidator
import cash.p.terminal.modules.send.contractvalidators.TronContractAddressValidator
import io.horizontalsystems.core.entities.BlockchainType
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val contractValidatorModule = module {
    EvmBlockchainManager.blockchainTypes.forEach { blockchainType ->
        single<ContractAddressValidator>(named(blockchainType.uid)) {
            EvmContractAddressValidator(get(), get())
        }
    }

    single<ContractAddressValidator>(named(BlockchainType.Solana.uid)) {
        SolanaContractAddressValidator(
            get()
        )
    }
    single<ContractAddressValidator>(named(BlockchainType.Tron.uid)) {
        TronContractAddressValidator(
            get()
        )
    }
    single<ContractAddressValidator>(named(BlockchainType.Ton.uid)) {
        TonContractAddressValidator(
            get()
        )
    }
    singleOf(::ExcludedContractValidator)

    singleOf(::AlphaAmlAddressValidator)
}
