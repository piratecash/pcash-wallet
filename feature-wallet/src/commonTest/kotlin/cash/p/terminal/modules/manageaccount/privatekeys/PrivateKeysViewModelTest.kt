package cash.p.terminal.modules.manageaccount.privatekeys

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.adapters.zcash.ZcashKeyExporter
import cash.p.terminal.core.managers.EvmBlockchains
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.ethereumkit.models.Chain
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class PrivateKeysViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val evmBlockchainManager = mockk<EvmBlockchains>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { evmBlockchainManager.getChain(any()) } returns Chain.Ethereum
        startKoin {
            modules(module {
                single { ZcashKeyExporter(TestDispatcherProvider(dispatcher, CoroutineScope(dispatcher))) }
            })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun init_englishMnemonicAccount_advertisesZcashKeys() {
        val viewModel = createViewModel(ENGLISH_WORDS)

        assertTrue(viewModel.viewState.hasZcashKeys)
    }

    @Test
    fun init_nonEnglishMnemonicAccount_advertisesZcashKeys() {
        val viewModel = createViewModel(SIMPLIFIED_CHINESE_WORDS)

        assertTrue(viewModel.viewState.hasZcashKeys)
    }

    @Test
    fun init_standaloneKeyAccount_hidesZcashKeys() {
        val viewModel = createViewModel(AccountType.EvmPrivateKey(BigInteger.ONE))

        assertFalse(viewModel.viewState.hasZcashKeys)
    }

    @Test
    fun init_saplingSpendingAccount_advertisesZcashKeys() {
        val viewModel = createViewModel(AccountType.ZCashSaplingKey(SAPLING_SPENDING_KEY))

        assertTrue(viewModel.viewState.hasZcashKeys)
    }

    @Test
    fun init_saplingViewingOnlyAccount_hidesZcashKeys() {
        val viewModel = createViewModel(AccountType.ZCashSaplingKey(SAPLING_VIEWING_KEY))

        assertFalse(viewModel.viewState.hasZcashKeys)
    }

    @Test
    fun init_evmPrivateKeyWithLeadingZeroByte_shows64HexChars() {
        val viewModel = createViewModel(AccountType.EvmPrivateKey(BigInteger(LEADING_ZERO_KEY, 16)))

        assertEquals(LEADING_ZERO_KEY, viewModel.viewState.evmPrivateKey)
    }

    @Test
    fun init_evmPrivateKeyWithHighBit_showsKeyWithoutSignByte() {
        val viewModel = createViewModel(AccountType.EvmPrivateKey(BigInteger(HIGH_BIT_KEY, 16)))

        assertEquals(HIGH_BIT_KEY, viewModel.viewState.evmPrivateKey)
    }

    private fun createViewModel(words: List<String>) =
        createViewModel(AccountType.Mnemonic(words, ""))

    private fun createViewModel(type: AccountType) = PrivateKeysViewModel(
        account = Account(
            id = "id",
            name = "name",
            type = type,
            origin = AccountOrigin.Restored,
            level = 0
        ),
        evmBlockchainManager = evmBlockchainManager
    )

    private companion object {
        const val SAPLING_SPENDING_KEY = "secret-extended-key-main1qsaplingspendingkey"
        const val SAPLING_VIEWING_KEY = "zxviews1qsaplingviewingkey"
        const val HIGH_BIT_KEY = "f1e2d3c4b5a69788796a5b4c3d2e1f00112233445566778899aabbccddeeff01"
        const val LEADING_ZERO_KEY = "00123456789abcdef0112233445566778899aabbccddeeff0011223344556677"

        val ENGLISH_WORDS = ("abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon about").split(" ")

        val SIMPLIFIED_CHINESE_WORDS = List(11) { "的" } + "在"
    }
}
