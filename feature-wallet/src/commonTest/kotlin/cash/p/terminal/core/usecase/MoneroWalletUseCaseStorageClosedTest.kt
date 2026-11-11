package cash.p.terminal.core.usecase

import cash.p.terminal.core.StorageLockedException
import cash.p.terminal.wallet.AccountType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

class MoneroWalletUseCaseStorageClosedTest {
    private val storage = mockk<MoneroWalletStorage> {
        every { walletsRoot() } throws StorageLockedException()
        every { newWalletPassword() } returns "password"
    }
    private val useCase = MoneroWalletUseCase(mockk(relaxed = true), GenerateMoneroWalletUseCase(storage), storage)

    @Test
    fun createNew_storageClosed_returnsNull() = runTest {
        assertNull(useCase.createNew())
    }

    @Test
    fun copyWalletFiles_storageClosed_returnsNull() = runTest {
        assertNull(useCase.copyWalletFiles(AccountType.MnemonicMonero(emptyList(), "password", 0, "wallet")))
    }

    @Test
    fun restore_storageClosedWithAKnownWalletName_returnsNull() = runTest {
        assertNull(useCase.restore(emptyList(), 0, "password", "wallet"))
    }

    @Test
    fun generate_storageClosed_returnsNull() {
        assertNull(GenerateMoneroWalletUseCase(storage)())
    }
}
