package cash.p.terminal.modules.manageaccount.publickeys

import cash.p.terminal.core.installEthereumCryptoProviderForTest
import cash.p.terminal.core.adapters.zcash.ZcashKeyExporter
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.modules.manageaccount.publickeys.PublicKeysModule.ZcashViewKeyKind
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.ethereumkit.models.Chain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class PublicKeysViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val evmBlockchainManager = mockk<EvmBlockchainManager>()
    private val zcashKeyExporter = mockk<ZcashKeyExporter>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        installEthereumCryptoProviderForTest()
        every { evmBlockchainManager.getChain(any()) } returns Chain.Ethereum
        every { zcashKeyExporter.supportsViewingKey(any()) } returns true
        startKoin { modules(module { single { zcashKeyExporter } }) }
    }

    @After
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun init_mnemonicAccount_showsTheKeyDerivedFromItsOwnPhrase() {
        val accountType = AccountType.Mnemonic(ENGLISH_WORDS, "")
        coEvery { zcashKeyExporter.viewingKey(accountType) } returns OWN_UFVK

        val viewModel = createViewModel(accountType)

        val viewState = viewModel.viewState
        assertNotNull(viewState.evmAddress)
        assertNotNull(viewState.extendedPublicKey)
        assertEquals(OWN_UFVK, viewState.zcashViewKey?.key)
        assertFalse(viewState.zcashViewKeyFailed)
        coVerify { zcashKeyExporter.viewingKey(accountType) }
    }

    @Test
    fun init_accountWithoutAZcashKey_showsNeitherKeyNorFailure() {
        val accountType = AccountType.EvmPrivateKey(BigInteger.ONE)
        coEvery { zcashKeyExporter.viewingKey(accountType) } returns null
        every { zcashKeyExporter.supportsViewingKey(accountType) } returns false

        val viewModel = createViewModel(accountType)

        assertNull(viewModel.viewState.zcashViewKey)
        assertFalse(viewModel.viewState.zcashViewKeyFailed)
    }

    @Test
    fun init_saplingSpendingKey_showsTheViewingKeyDerivedFromItsOwnKey() {
        val accountType = AccountType.ZCashSaplingKey(SAPLING_SPENDING_KEY)
        coEvery { zcashKeyExporter.viewingKey(accountType) } returns SAPLING_VIEWING_KEY

        val viewModel = createViewModel(accountType)

        assertEquals(SAPLING_VIEWING_KEY, viewModel.viewState.zcashViewKey?.key)
        assertEquals(ZcashViewKeyKind.Sapling, viewModel.viewState.zcashViewKey?.kind)
    }

    @Test
    fun init_saplingSpendingKeyDerivationFails_doesNotFallBackToActiveAccountKey() {
        val accountType = AccountType.ZCashSaplingKey(SAPLING_SPENDING_KEY)
        coEvery { zcashKeyExporter.viewingKey(accountType) } returns null

        val viewModel = createViewModel(accountType)

        assertNull(viewModel.viewState.zcashViewKey)
    }

    @Test
    fun init_saplingSpendingKeyDerivationFails_reportsFailure() {
        val accountType = AccountType.ZCashSaplingKey(SAPLING_SPENDING_KEY)
        coEvery { zcashKeyExporter.viewingKey(accountType) } returns null

        val viewModel = createViewModel(accountType)

        assertTrue(viewModel.viewState.zcashViewKeyFailed)
    }

    @Test
    fun init_ufvkAccount_showsStoredKeyAsUnified() {
        val accountType = AccountType.ZCashUfvKey(OWN_UFVK)
        coEvery { zcashKeyExporter.viewingKey(accountType) } returns OWN_UFVK

        val viewModel = createViewModel(accountType)

        assertEquals(OWN_UFVK, viewModel.viewState.zcashViewKey?.key)
        assertEquals(ZcashViewKeyKind.Unified, viewModel.viewState.zcashViewKey?.kind)
    }

    private fun createViewModel(type: AccountType) = PublicKeysViewModel(
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
        const val OWN_UFVK = "uview1qownaccountkey"

        val ENGLISH_WORDS = ("abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon about").split(" ")
    }
}
