package cash.p.terminal.modules.nft.renew

import cash.p.terminal.core.BalanceSettingsStorage
import cash.p.terminal.core.nft.DOMAIN_RENEW_SOON_DAYS
import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.core.nft.NftDomainExpiry
import cash.p.terminal.core.nft.NftOwner
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.managers.IBalanceHiddenManager
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NftDomainRenewalViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val owner = NftOwner("account", BlockchainType.Ton, "owner")
    private val otherOwner = NftOwner("other-account", BlockchainType.Ton, "other-owner")

    private val wallet = wallet(AccountType.Mnemonic(listOf("word"), ""))
    private val otherWallet = wallet(AccountType.Mnemonic(listOf("other"), ""))
    private val watchWallet = wallet(AccountType.TonAddress("watched"))
    private val jettonWallet = wallet(AccountType.Mnemonic(listOf("jetton"), ""))
    private val walletsFlow = MutableStateFlow(listOf(wallet))
    private val triggers = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    private val showNft = MutableStateFlow(true)
    private val settings = mockk<BalanceSettingsStorage> {
        every { showNftCollectionsFlow } returns showNft
    }

    private val repository = mockk<NftCollectionsRepository> {
        coEvery { owners(listOf(wallet)) } returns setOf(owner)
        coEvery { owners(listOf(otherWallet)) } returns setOf(otherOwner)
        coEvery { owners(listOf(jettonWallet)) } returns setOf(owner)
        coEvery { owners(emptyList()) } returns emptySet()
        coEvery { expiringDomains(emptySet(), any()) } returns emptyList()
        every { ownersFlow(any()) } answers { callOriginal() }
        every { supportsSection(any()) } answers { firstArg<Wallet>() !== jettonWallet }
        every { domainExpiryTriggers(any()) } returns triggers
        every { expiringDomainsFlow(any(), any(), any()) } answers { callOriginal() }
    }
    private val walletManager = mockk<IWalletManager> {
        every { activeWalletsFlow } returns walletsFlow
    }
    private val globalBalanceHidden = MutableStateFlow(false)
    private val balanceHiddenManager = mockk<IBalanceHiddenManager> {
        every { balanceHiddenFlow } returns globalBalanceHidden
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun wallet(type: AccountType): Wallet {
        val account = Account("id-${type.hashCode()}", "name", type, AccountOrigin.Created, level = 0)
        return mockk { every { this@mockk.account } returns account }
    }

    private fun createViewModel() = NftDomainRenewalViewModel(repository, walletManager, balanceHiddenManager, settings)

    private fun domain(address: String, owner: NftOwner = this.owner, onSale: Boolean = false) =
        NftDomainExpiry(owner, address, "$address.ton", expiresAt = 0, isOnSale = onSale)

    private fun readReturns(owner: NftOwner, domains: List<NftDomainExpiry>?) {
        coEvery { repository.expiringDomains(setOf(owner), DOMAIN_RENEW_SOON_DAYS) } returns domains
    }

    @Test
    fun showNftCollections_off_readsNoDomains() {
        readReturns(owner, listOf(domain("a")))
        showNft.value = false

        val viewModel = createViewModel()
        viewModel.onResume()

        assertTrue(viewModel.uiState.domains.isEmpty())
        coVerify(exactly = 0) { repository.expiringDomains(setOf(owner), any()) }
    }

    @Test
    fun showNftCollections_turnedOnAndOff_showsThenDropsDomains() {
        readReturns(owner, listOf(domain("a")))
        showNft.value = false
        val viewModel = createViewModel()

        showNft.value = true
        assertEquals(listOf(domain("a")), viewModel.uiState.domains)

        showNft.value = false
        assertTrue(viewModel.uiState.domains.isEmpty())
    }

    @Test
    fun init_noExpiringDomains_hasNoDomains() {
        readReturns(owner, emptyList())

        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.domains.isEmpty())
    }

    @Test
    fun init_expiringDomains_exposesThem() {
        val domains = listOf(domain("a"), domain("b", onSale = true))
        readReturns(owner, domains)

        val viewModel = createViewModel()

        assertEquals(domains, viewModel.uiState.domains)
    }

    @Test
    fun trigger_firstReadWasNull_showsDomainsAfterKitAppeared() {
        readReturns(owner, null)
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.domains.isEmpty())

        readReturns(owner, listOf(domain("a")))
        triggers.tryEmit(Unit)

        assertEquals(listOf(domain("a")), viewModel.uiState.domains)
    }

    @Test
    fun trigger_laterReadIsNullForSameOwners_keepsPreviousDomains() {
        readReturns(owner, listOf(domain("a")))
        val viewModel = createViewModel()

        readReturns(owner, null)
        triggers.tryEmit(Unit)

        assertEquals(listOf(domain("a")), viewModel.uiState.domains)
    }

    @Test
    fun onResume_rereadsDomains() {
        readReturns(owner, listOf(domain("a")))
        val viewModel = createViewModel()

        readReturns(owner, emptyList())
        viewModel.onResume()

        assertTrue(viewModel.uiState.domains.isEmpty())
    }

    @Test
    fun ownersChange_newOwnerReadIsNull_doesNotCarryOverPreviousDomains() {
        readReturns(owner, listOf(domain("a")))
        readReturns(otherOwner, null)
        val viewModel = createViewModel()

        walletsFlow.value = listOf(otherWallet)

        assertEquals(setOf(otherOwner), viewModel.uiState.owners)
        assertTrue(viewModel.uiState.domains.isEmpty())
    }

    @Test
    fun init_watchOnlyWallet_isNotQueried() {
        walletsFlow.value = listOf(watchWallet)
        readReturns(owner, listOf(domain("a")))

        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.domains.isEmpty())
        coVerify(exactly = 0) { repository.owners(listOf(watchWallet)) }
    }

    @Test
    fun init_onlyJettonWalletOfOwnerActive_noDomainsAndExpiryNotQueried() {
        walletsFlow.value = listOf(jettonWallet)
        readReturns(owner, listOf(domain("a")))

        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.owners.isEmpty())
        assertTrue(viewModel.uiState.domains.isEmpty())
        coVerify(exactly = 0) { repository.expiringDomains(setOf(owner), any()) }
    }

    @Test
    fun selected_default_excludesOnSaleDomains() {
        readReturns(owner, listOf(domain("a"), domain("b", onSale = true), domain("c")))

        val viewModel = createViewModel()

        assertEquals(listOf("a", "c"), viewModel.uiState.selected.map { it.nftAddress })
    }

    @Test
    fun onToggle_selectedDomain_deselectsAndSelectsAgain() {
        readReturns(owner, listOf(domain("a"), domain("c")))
        val viewModel = createViewModel()

        viewModel.onToggle(domain("a"))
        assertEquals(listOf("c"), viewModel.uiState.selected.map { it.nftAddress })

        viewModel.onToggle(domain("a"))
        assertEquals(listOf("a", "c"), viewModel.uiState.selected.map { it.nftAddress })
    }

    @Test
    fun uiState_balanceHidden_hiddenWithDomainsKept() {
        readReturns(owner, listOf(domain("a")))
        globalBalanceHidden.value = true

        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.hidden)
        assertEquals(listOf(domain("a")), viewModel.uiState.domains)
    }

    @Test
    fun balanceHiddenFlow_toggled_hiddenFollowsLive() {
        readReturns(owner, listOf(domain("a")))
        val viewModel = createViewModel()
        assertFalse(viewModel.uiState.hidden)

        globalBalanceHidden.value = true
        assertTrue(viewModel.uiState.hidden)

        globalBalanceHidden.value = false
        assertFalse(viewModel.uiState.hidden)
    }

    @Test
    fun ownersChange_balanceHidden_staysHidden() {
        readReturns(owner, listOf(domain("a")))
        readReturns(otherOwner, emptyList())
        globalBalanceHidden.value = true
        val viewModel = createViewModel()

        walletsFlow.value = listOf(otherWallet)

        assertTrue(viewModel.uiState.hidden)
    }
}
