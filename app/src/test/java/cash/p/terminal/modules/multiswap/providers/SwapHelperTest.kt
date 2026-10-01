package cash.p.terminal.modules.multiswap.providers

import cash.p.terminal.core.App
import cash.p.terminal.core.managers.ThorchainKitManagers
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IReceiveAdapter
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.math.BigDecimal
import kotlin.test.assertFailsWith

class SwapHelperTest {

    private val thorchainKitManagers = mockk<ThorchainKitManagers>()
    private val runeToken = Token(
        coin = Coin(uid = "thorchain", name = "THORChain", code = "RUNE"),
        blockchain = Blockchain(BlockchainType.Thorchain, "THORChain", null),
        type = TokenType.Native,
        decimals = 8,
    )

    @After
    fun tearDown() {
        unmockkAll()
        stopKoin()
    }

    @Test
    fun requiredInput_amountInMaxPresent_returnsMaximum() {
        assertEquals(
            BigDecimal("1.2"),
            requiredInput(BigDecimal.ONE, BigDecimal("1.2")),
        )
    }

    @Test
    fun insufficientAllowanceCaution_allowanceBelowRequiredInput_returnsTypedCaution() {
        assertTrue(
            insufficientAllowanceCaution(
                allowance = BigDecimal.ONE,
                requiredInput = BigDecimal("1.2"),
            ) is InsufficientAllowanceCaution
        )
    }

    @Test
    fun insufficientAllowanceCaution_allowanceCoversRequiredInput_returnsNull() {
        assertNull(
            insufficientAllowanceCaution(
                allowance = BigDecimal("1.2"),
                requiredInput = BigDecimal("1.2"),
            )
        )
    }

    @Test
    fun getReceiveAddressForToken_thorchainWithoutWalletMnemonic_returnsKitAddress() = runTest {
        val account = account(AccountType.Mnemonic(List(11) { "abandon" } + "about", ""))
        mockActiveAccount(account)
        every { thorchainKitManagers.forType(BlockchainType.Thorchain).getAddress(account) } returns THOR_ADDRESS

        assertEquals(THOR_ADDRESS, SwapHelper.getReceiveAddressForToken(runeToken))
    }

    @Test
    fun getReceiveAddressForToken_thorchainWithoutWalletNonMnemonic_throwsNoDestinationAddress() = runTest {
        mockActiveAccount(account(AccountType.EvmAddress("0x1111111111111111111111111111111111111111")))

        assertFailsWith<SwapError.NoDestinationAddress> { SwapHelper.getReceiveAddressForToken(runeToken) }
    }

    private fun mockActiveAccount(account: Account) {
        startKoin { modules(module { single { thorchainKitManagers } }) }
        mockkObject(App)
        every { App.adapterManager } returns mockk<IAdapterManager> {
            every { getAdapterForToken<IReceiveAdapter>(runeToken) } returns null
        }
        every { App.accountManager } returns mockk<IAccountManager> {
            every { activeAccount } returns account
        }
        every { App.evmBlockchainManager } returns mockk(relaxed = true)
    }

    private fun account(type: AccountType) = Account(
        id = "account-id",
        name = "Account",
        type = type,
        origin = AccountOrigin.Created,
        level = 0,
    )

    private companion object {
        const val THOR_ADDRESS = "thor166n4w5039meulfa3p6ydg60ve6ueac7tlt0jws"
    }
}
