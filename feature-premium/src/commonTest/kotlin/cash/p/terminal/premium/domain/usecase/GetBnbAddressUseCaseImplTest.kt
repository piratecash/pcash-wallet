package cash.p.terminal.premium.domain.usecase

import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.MnemonicSeed
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetBnbAddressUseCaseImplTest {

    private val useCase = GetBnbAddressUseCaseImpl(SeedToEvmAddressUseCase(), mockk(), mockk())

    @Test
    fun getAddress_mnemonicAccount_keepsAddressDerivedFromPhrase() = runTest {
        val address = useCase.getAddress(account(AccountType.Mnemonic(WORDS, "")), requestScanTangemIfNotFound = false)

        assertEquals(PHRASE_ADDRESS, address?.lowercase())
    }

    @Test
    fun getAddress_slip39Account_derivesFromItsSeed() = runTest {
        val type = AccountType.Slip39(listOf("academic"), "", MnemonicSeed.derive(WORDS))

        val address = useCase.getAddress(account(type), requestScanTangemIfNotFound = false)

        assertEquals(PHRASE_ADDRESS, address?.lowercase())
    }

    private fun account(type: AccountType) =
        Account(id = "id", name = "name", type = type, origin = AccountOrigin.Restored, level = 0)

    private companion object {
        val WORDS = "elephant crew industry destroy layer consider aspect split recycle intact assist flat".split(" ")

        // Produced by the phrase-based implementation before the use case switched to seeds.
        const val PHRASE_ADDRESS = "0x7b7ebe8044d5e9452fae1cd33304a6d2eac6c28d"
    }
}
