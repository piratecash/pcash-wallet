package cash.p.terminal.modules.nft

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.nft.NftCollectionsRepository
import cash.p.terminal.core.nft.NftLiveState
import cash.p.terminal.core.nft.NftProvider
import cash.p.terminal.entities.NftItemRecord
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class NftTransferAvailabilityProviderTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val offlineModeManager = mockk<OfflineModeManager> {
        every { isNetworkPaused(any<OfflineKey>()) } returns false
    }
    private val nftProvider = mockk<NftProvider> {
        every { blockchainType } returns BlockchainType.Ton
        every { supportsSection(any()) } returns true
    }
    private val walletManager = mockk<IWalletManager>()
    private val availabilityProvider = NftTransferAvailabilityProvider(
        walletManager,
        NftCollectionsRepository(
            mockk(),
            listOf(nftProvider),
            offlineModeManager,
            TestDispatcherProvider(dispatcher, TestScope(dispatcher)),
        ),
    )

    @Test
    fun check_noActiveWalletOfItemAccount_returnsHidden() = runTest(dispatcher) {
        givenWallets(wallet(accountId = "other-account"))

        assertEquals(NftTransferAvailability.Hidden, availabilityProvider.check(item))
        assertNoLiveRequest()
    }

    @Test
    fun check_walletOwnsOtherAddress_returnsHidden() = runTest(dispatcher) {
        givenWallets(wallet(ownerAddress = OTHER_ADDRESS))

        assertEquals(NftTransferAvailability.Hidden, availabilityProvider.check(item))
    }

    @Test
    fun check_walletNotSupportedBySection_returnsHidden() = runTest(dispatcher) {
        givenWallets(wallet())
        every { nftProvider.supportsSection(any()) } returns false

        assertEquals(NftTransferAvailability.Hidden, availabilityProvider.check(item))
    }

    @Test
    fun check_watchAccount_returnsWatchOnlyWithoutRequest() = runTest(dispatcher) {
        givenWallets(wallet(watch = true, accountType = AccountType.TonAddress(ADDRESS)))

        assertEquals(unavailable(NftTransferUnavailableReason.WatchOnly), availabilityProvider.check(item))
        assertNoLiveRequest()
    }

    @Test
    fun check_accountWithoutTonConnect_returnsHiddenWithoutRequest() = runTest(dispatcher) {
        givenWallets(wallet(accountType = mockk<AccountType.EvmPrivateKey>()))

        assertEquals(NftTransferAvailability.Hidden, availabilityProvider.check(item))
        assertNoLiveRequest()
    }

    @Test
    fun check_networkPaused_returnsCheckFailedWithoutRequest() = runTest(dispatcher) {
        givenWallets(wallet())
        every { offlineModeManager.isNetworkPaused(OfflineKey(ACCOUNT_ID, BlockchainType.Ton)) } returns true

        assertEquals(unavailable(NftTransferUnavailableReason.CheckFailed), availabilityProvider.check(item))
        assertNoLiveRequest()
    }

    @Test
    fun check_sourceNotAvailable_returnsCheckFailed() = runTest(dispatcher) {
        givenWallets(wallet())
        coEvery { nftProvider.liveState(ADDRESS, NFT) } returns null

        assertEquals(unavailable(NftTransferUnavailableReason.CheckFailed), availabilityProvider.check(item))
    }

    @Test
    fun check_requestFails_returnsCheckFailed() = runTest(dispatcher) {
        givenWallets(wallet())
        coEvery { nftProvider.liveState(ADDRESS, NFT) } throws IllegalStateException("boom")

        assertEquals(unavailable(NftTransferUnavailableReason.CheckFailed), availabilityProvider.check(item))
    }

    @Test
    fun check_requestCancelled_rethrows() = runTest(dispatcher) {
        givenWallets(wallet())
        coEvery { nftProvider.liveState(ADDRESS, NFT) } throws CancellationException()

        assertFailsWith<CancellationException> { availabilityProvider.check(item) }
    }

    @Test
    fun check_onSaleUnderAnotherOwner_returnsOnSale() = runTest(dispatcher) {
        givenWallets(wallet())
        givenLiveState(NftLiveState(OTHER_ADDRESS, isOnSale = true, domain = null))

        assertEquals(unavailable(NftTransferUnavailableReason.OnSale), availabilityProvider.check(item))
    }

    @Test
    fun check_liveOwnerDiffers_returnsNotOwner() = runTest(dispatcher) {
        givenWallets(wallet())
        givenLiveState(NftLiveState(OTHER_ADDRESS, isOnSale = false, domain = null))

        assertEquals(unavailable(NftTransferUnavailableReason.NotOwner), availabilityProvider.check(item))
    }

    @Test
    fun check_liveOwnerUnknown_returnsNotOwner() = runTest(dispatcher) {
        givenWallets(wallet())
        givenLiveState(NftLiveState(ownerAddress = null, isOnSale = false, domain = null))

        assertEquals(unavailable(NftTransferUnavailableReason.NotOwner), availabilityProvider.check(item))
    }

    @Test
    fun check_ownedAndNotOnSale_returnsAvailableWithWalletAndDomain() = runTest(dispatcher) {
        val wallet = wallet()
        givenWallets(wallet)
        givenLiveState(NftLiveState(ADDRESS, isOnSale = false, domain = "alex.ton"))

        val available = assertIs<NftTransferAvailability.Available>(availabilityProvider.check(item))

        assertSame(wallet, available.wallet)
        assertEquals("alex.ton", available.domain)
    }

    @Test
    fun check_v4R2AndW5Wallets_usesWalletOwningTheNft() = runTest(dispatcher) {
        val v4r2 = wallet(ownerAddress = OTHER_ADDRESS)
        val w5 = wallet(ownerAddress = ADDRESS)
        givenWallets(v4r2, w5)
        givenLiveState(NftLiveState(ADDRESS, isOnSale = false, domain = null))

        val available = assertIs<NftTransferAvailability.Available>(availabilityProvider.check(item))

        assertSame(w5, available.wallet)
        coVerify(exactly = 0) { nftProvider.liveState(OTHER_ADDRESS, any()) }
    }

    private fun givenWallets(vararg wallets: Wallet) {
        every { walletManager.activeWallets } returns wallets.toList()
    }

    private fun givenLiveState(state: NftLiveState) {
        coEvery { nftProvider.liveState(ADDRESS, NFT) } returns state
    }

    private fun assertNoLiveRequest() {
        coVerify(exactly = 0) { nftProvider.liveState(any(), any()) }
    }

    private fun unavailable(reason: NftTransferUnavailableReason) = NftTransferAvailability.Unavailable(reason)

    private fun wallet(
        accountId: String = ACCOUNT_ID,
        ownerAddress: String = ADDRESS,
        watch: Boolean = false,
        accountType: AccountType = mockk<AccountType.Mnemonic>(),
    ): Wallet {
        val walletToken = mockk<Token> {
            every { blockchainType } returns BlockchainType.Ton
        }
        val walletAccount = mockk<Account> {
            every { id } returns accountId
            every { isWatchAccount } returns watch
            every { type } returns accountType
        }
        val wallet = mockk<Wallet> {
            every { token } returns walletToken
            every { account } returns walletAccount
        }
        coEvery { nftProvider.ownerAddresses(listOf(wallet)) } returns setOf(ownerAddress)
        return wallet
    }

    private val item = NftItemRecord(
        accountId = ACCOUNT_ID,
        blockchainType = BlockchainType.Ton,
        ownerAddress = ADDRESS,
        address = NFT,
        position = 0,
        name = null,
        description = null,
        realOwnerAddress = null,
        collectionAddress = null,
        collectionName = null,
        collectionDescription = null,
        trust = NftTrust.Whitelist,
        verified = false,
        imageUrl = null,
        largeImageUrl = null,
    )

    private companion object {
        const val ACCOUNT_ID = "account-id"
        const val ADDRESS = "owner-address"
        const val OTHER_ADDRESS = "other-owner-address"
        const val NFT = "nft-address"
    }
}
