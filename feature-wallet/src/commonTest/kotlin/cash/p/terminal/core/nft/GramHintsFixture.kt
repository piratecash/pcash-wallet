package cash.p.terminal.core.nft

import cash.p.terminal.core.AccountTokenSupport
import cash.p.terminal.core.BalanceSettingsStorage
import cash.p.terminal.core.managers.OfflineKey
import cash.p.terminal.core.managers.OfflineModeManager
import cash.p.terminal.core.managers.WalletActivator
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IDeletedWalletRestorer
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TonAsset
import cash.p.terminal.wallet.entities.TonWalletVersion
import cash.p.terminal.wallet.entities.tokenQuery
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/** A real [NftGramHintsRepository] over mocked chain access, shared by the GRAM hint tests. */
internal class GramHintsFixture {

    val account = account("account", AccountType.Mnemonic(listOf("word"), ""))
    val supportedVersions = TonWalletVersion.entries.toMutableSet()
    val nftCounts = mutableMapOf<TonWalletVersion, Int?>()
    var networkPaused = false
    val showNft = MutableStateFlow(true)
    private var dismissed = emptySet<String>()

    val tokens = TonWalletVersion.entries.associateWith { version ->
        mockk<Token> { every { tokenQuery } returns TonAsset.Native(version).tokenQuery }
    }

    val marketKit = mockk<MarketKitWrapper> {
        every { token(any()) } answers { tokens.values.firstOrNull { it.tokenQuery == firstArg() } }
    }
    val accountTokenSupport = mockk<AccountTokenSupport> {
        every { supports(any(), any()) } answers {
            tokens.entries.first { it.value == firstArg() }.key in supportedVersions
        }
    }
    val tonNftProvider = mockk<TonNftProvider> {
        coEvery { countNfts(any(), any()) } answers { nftCounts[secondArg()] }
        coEvery { hasAddress(any(), any()) } returns true
    }
    val offlineModeManager = mockk<OfflineModeManager> {
        every { isNetworkPaused(any<OfflineKey>()) } answers { networkPaused }
    }
    val settings = mockk<BalanceSettingsStorage> {
        every { showNftCollectionsFlow } returns showNft
        every { showNftCollections = any() } answers { showNft.value = firstArg() }
        every { dismissedNftVersionHints } answers { dismissed }
        every { dismissedNftVersionHints = any() } answers { dismissed = firstArg() }
    }
    val walletActivator = mockk<WalletActivator> {
        coEvery { activateWalletsSuspended(any(), any()) } just Runs
    }

    val deletedWalletRestorer = mockk<IDeletedWalletRestorer> {
        coEvery { unmarkAsDeleted(any(), any()) } just Runs
    }

    val repository = NftGramHintsRepository(
        marketKit, accountTokenSupport, tonNftProvider, offlineModeManager, settings, walletActivator,
        deletedWalletRestorer,
    )

    fun wallet(version: TonWalletVersion, owner: Account = account): Wallet = wallet(tokens.getValue(version), owner)

    fun jettonWallet(version: TonWalletVersion, owner: Account = account): Wallet =
        wallet(mockk<Token> { every { tokenQuery } returns TonAsset.Jetton("jetton-master", version).tokenQuery }, owner)

    private fun wallet(walletToken: Token, owner: Account): Wallet = mockk {
        every { token } returns walletToken
        every { this@mockk.account } returns owner
    }

    fun account(id: String, type: AccountType) =
        Account(id = id, name = id, type = type, origin = AccountOrigin.Created, level = 0)
}
