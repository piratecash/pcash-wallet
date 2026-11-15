package cash.p.terminal.core.nft

import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.TonAsset
import cash.p.terminal.wallet.entities.TonWalletVersion
import cash.p.terminal.wallet.entities.tokenQuery
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NftGramHintsRepositoryTest {

    private val fixture = GramHintsFixture()
    private val repository = fixture.repository
    private val account = fixture.account
    private val bothVersions = TonWalletVersion.entries

    @Test
    fun eligibleVersions_versionNotSupportedByAccount_isLeftOut() {
        fixture.supportedVersions -= TonWalletVersion.W5

        assertEquals(listOf(TonWalletVersion.V4R2), repository.eligibleVersions(account))
    }

    @Test
    fun nftCounts_watchAccount_readsNothing() = runTest {
        val watched = fixture.account("watch", AccountType.TonAddress("UQ-watched"))

        assertEquals(emptyMap(), repository.nftCounts(watched, bothVersions))
        coVerify(exactly = 0) { fixture.tonNftProvider.countNfts(any(), any()) }
    }

    @Test
    fun nftCounts_networkPaused_readsNothing() = runTest {
        fixture.networkPaused = true

        assertEquals(emptyMap(), repository.nftCounts(account, bothVersions))
        coVerify(exactly = 0) { fixture.tonNftProvider.countNfts(any(), any()) }
    }

    @Test
    fun nftCounts_successCached_failureRetried() = runTest {
        fixture.nftCounts[TonWalletVersion.V4R2] = 3
        fixture.nftCounts[TonWalletVersion.W5] = null
        repository.nftCounts(account, bothVersions)

        fixture.nftCounts[TonWalletVersion.W5] = 5
        val counts = repository.nftCounts(account, bothVersions)

        assertEquals(mapOf(TonWalletVersion.V4R2 to 3, TonWalletVersion.W5 to 5), counts)
        coVerify(exactly = 1) { fixture.tonNftProvider.countNfts(account, TonWalletVersion.V4R2) }
        coVerify(exactly = 2) { fixture.tonNftProvider.countNfts(account, TonWalletVersion.W5) }
    }

    @Test
    fun dismiss_oneVersion_hidesOnlyThatVersionOfThatAccount() {
        val other = fixture.account("other", AccountType.Mnemonic(listOf("word"), ""))

        repository.dismiss(account, TonWalletVersion.W5)

        assertTrue(repository.isDismissed(account, TonWalletVersion.W5))
        assertFalse(repository.isDismissed(account, TonWalletVersion.V4R2))
        assertFalse(repository.isDismissed(other, TonWalletVersion.W5))
    }

    @Test
    fun nftCounts_pausedDuringScan_readsNoFurtherVersion() = runTest {
        coEvery { fixture.tonNftProvider.countNfts(account, TonWalletVersion.V4R2) } answers {
            fixture.networkPaused = true
            1
        }

        assertEquals(mapOf(TonWalletVersion.V4R2 to 1), repository.nftCounts(account, bothVersions))
        coVerify(exactly = 0) { fixture.tonNftProvider.countNfts(any(), TonWalletVersion.W5) }
    }

    @Test
    fun add_previouslyRemovedVersion_clearsRemovalMarkThenActivatesThatVersionOnly() = runTest {
        val query = TonAsset.Native(TonWalletVersion.W5).tokenQuery

        assertTrue(repository.add(account, TonWalletVersion.W5))

        coVerifyOrder {
            fixture.deletedWalletRestorer.unmarkAsDeleted(account.id, listOf(query.id))
            fixture.walletActivator.activateWalletsSuspended(account, listOf(query))
        }
    }

    @Test
    fun add_noAddress_returnsFalseWithoutActivating() = runTest {
        coEvery { fixture.tonNftProvider.hasAddress(account, TonWalletVersion.V4R2) } returns false

        assertFalse(repository.add(account, TonWalletVersion.V4R2))
        coVerify(exactly = 0) { fixture.walletActivator.activateWalletsSuspended(any(), any()) }
    }
}
