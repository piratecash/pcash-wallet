package cash.p.terminal.core.adapters

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.core.managers.EvmLabelManager
import cash.p.terminal.core.managers.StackingManager
import cash.p.terminal.data.repository.EvmTransactionRepository
import cash.p.terminal.wallet.WalletFactory
import io.horizontalsystems.erc20kit.core.Erc20Kit
import io.mockk.Called
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class Eip20AdapterLocalDataContractTest {

    @Test
    fun attachLocalData_eip20Adapter_doesNotTouchNetwork() {
        val repository = mockk<EvmTransactionRepository>(relaxed = true)
        val eip20Kit = mockk<Erc20Kit>(relaxed = true)
        val wallet = WalletFactory.previewStakingWallet()
        val adapter = Eip20Adapter(
            eip20Kit = eip20Kit,
            evmTransactionRepository = repository,
            contractAddress = "0x0000000000000000000000000000000000000001",
            baseToken = wallet.token,
            coinManager = mockk<ICoinManager>(relaxed = true),
            wallet = wallet,
            evmLabelManager = mockk<EvmLabelManager>(relaxed = true),
            stackingManager = mockk<StackingManager>(relaxed = true),
        )
        clearMocks(eip20Kit, answers = false)

        adapter.attachLocalData()

        verify { eip20Kit wasNot Called }
    }
}
