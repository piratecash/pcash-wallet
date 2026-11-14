package cash.p.terminal.core.nft

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.core.managers.TonKitWrapper
import cash.p.terminal.core.providers.SharedAppConfig
import cash.p.terminal.entities.NftTrust
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.TonWalletVersion
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.api.TonApi
import io.horizontalsystems.tonkit.core.TonKit
import io.horizontalsystems.tonkit.models.DnsExpiringItem
import io.horizontalsystems.tonkit.models.EventInfo
import io.horizontalsystems.tonkit.models.NftButton
import io.horizontalsystems.tonkit.models.NftCollection
import io.horizontalsystems.tonkit.models.NftItem
import io.horizontalsystems.tonkit.models.NftPreview
import io.horizontalsystems.tonkit.models.TagQuery
import io.horizontalsystems.tonkit.models.NftTrust as KitNftTrust
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class TonNftProviderTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val kits = MutableStateFlow<Map<TonWalletVersion, TonKitWrapper>>(emptyMap())
    private val tonKitManager = mockk<TonKitManager> {
        every { startedKitsFlow } returns kits
    }
    private val provider = TonNftProvider(tonKitManager, TestDispatcherProvider(dispatcher, TestScope(dispatcher)))

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun countNfts_blacklistedAndHiddenItems_areNotCounted() = test {
        val api = countableApi()
        coEvery { api.getAccountNftItems(Address.parse(OWNER), any()) } returns listOf(
            nft("bad", trust = KitNftTrust.BLACKLIST),
            nft("hidden", renderType = "hidden"),
            nft("good"),
            nft("gray", trust = KitNftTrust.GRAYLIST),
        )

        assertEquals(2, provider.countNfts(mockk(), TonWalletVersion.W5))
    }

    @Test
    fun countNfts_requestFails_returnsNull() = test {
        val api = countableApi()
        coEvery { api.getAccountNftItems(any(), any()) } throws IllegalStateException("offline")

        assertNull(provider.countNfts(mockk(), TonWalletVersion.W5))
    }

    private fun countableApi(): TonApi {
        val api = mockk<TonApi>()
        mockkObject(TonKit.Companion, SharedAppConfig)
        every { SharedAppConfig.tonApiKeys } returns emptyList()
        every { TonKit.getTonApi(any(), any()) } returns api
        coEvery { tonKitManager.getReceiveAddress(any(), TonWalletVersion.W5) } returns OWNER
        return api
    }

    @Test
    fun fetch_blacklistedItem_isKeptAsBlacklist() = test {
        startKit(items = listOf(nft("bad", trust = KitNftTrust.BLACKLIST), nft("good")))

        assertEquals(
            listOf("bad" to NftTrust.Blacklist, "good" to NftTrust.Whitelist),
            provider.fetch(OWNER)?.map { it.address to it.trust },
        )
    }

    @Test
    fun fetch_hiddenRenderType_isFiltered() = test {
        startKit(
            items = listOf(
                nft("hidden", renderType = "hidden"),
                nft("hiddenBlacklisted", renderType = "hidden", trust = KitNftTrust.BLACKLIST),
                nft("shown", renderType = "video"),
            )
        )

        assertEquals(listOf("shown"), provider.fetch(OWNER)?.map { it.address })
    }

    @Test
    fun fetch_noneGraylistAndWhitelist_areKeptWithMappedTrust() = test {
        startKit(
            items = listOf(
                nft("none", trust = KitNftTrust.NONE),
                nft("gray", trust = KitNftTrust.GRAYLIST),
                nft("white", trust = KitNftTrust.WHITELIST),
            ),
        )

        assertEquals(
            listOf(NftTrust.None, NftTrust.Graylist, NftTrust.Whitelist),
            provider.fetch(OWNER)?.map { it.trust },
        )
    }

    @Test
    fun fetch_kitVerifiedFlag_isCopiedToDraft() = test {
        startKit(items = listOf(nft("yes", verified = true), nft("no", verified = false)))

        assertEquals(listOf(true, false), provider.fetch(OWNER)?.map { it.verified })
    }

    @Test
    fun fetch_kitSaleState_isCopiedToDraft() = test {
        startKit(items = listOf(nft("sale", isOnSale = true), nft("free", isOnSale = false)))

        assertEquals(listOf(true, false), provider.fetch(OWNER)?.map { it.inSale })
    }

    @Test
    fun fetch_lottieUrl_keepsTrimmedWebUrlsOnly() = test {
        val tooLong = "https://a.io/" + "x".repeat(2048)
        startKit(
            items = listOf(
                nft("https", lottie = " https://nft.fragment.com/gift/a.lottie.json "),
                nft("http", lottie = "HTTP://example.com/b.json"),
                nft("ipfs", lottie = "ipfs://QmHash"),
                nft("script", lottie = "javascript:alert(1)"),
                nft("long", lottie = tooLong),
                nft("none", lottie = null),
            )
        )

        assertEquals(
            listOf("https://nft.fragment.com/gift/a.lottie.json", "HTTP://example.com/b.json", null, null, null, null),
            provider.fetch(OWNER)?.map { it.lottieUrl }
        )
    }

    @Test
    fun fetch_exactPreviewsPresent_picksCardAndLargeSizes() = test {
        val previews = listOf(
            NftPreview("100x100", "https://cdn/100"),
            NftPreview("500x500", "https://cdn/500"),
            NftPreview("1500x1500", "https://cdn/1500"),
        )
        startKit(items = listOf(nft("a", previews = previews)))

        val draft = provider.fetch(OWNER)?.single()

        assertEquals("https://cdn/500", draft?.imageUrl)
        assertEquals("https://cdn/1500", draft?.largeImageUrl)
    }

    @Test
    fun fetch_exactPreviewsMissing_picksNearestResolution() = test {
        val previews = listOf(NftPreview("100x100", "https://cdn/100"), NftPreview("1000x1000", "https://cdn/1000"))
        startKit(items = listOf(nft("a", previews = previews)))

        val draft = provider.fetch(OWNER)?.single()

        assertEquals("https://cdn/100", draft?.imageUrl)
        assertEquals("https://cdn/1000", draft?.largeImageUrl)
    }

    @Test
    fun fetch_noPreviews_imagesAreNull() = test {
        startKit(items = listOf(nft("a", previews = emptyList())))

        val draft = provider.fetch(OWNER)?.single()

        assertNull(draft?.imageUrl)
        assertNull(draft?.largeImageUrl)
    }

    @Test
    fun fetch_nonHttpsPreview_isDroppedEvenWhenExact() = test {
        val previews = listOf(NftPreview("500x500", "http://cdn/500"), NftPreview("100x100", "https://cdn/100"))
        startKit(items = listOf(nft("a", previews = previews)))

        assertEquals("https://cdn/100", provider.fetch(OWNER)?.single()?.imageUrl)
    }

    @Test
    fun fetch_onlyNonHttpsPreviews_imagesAreNull() = test {
        startKit(items = listOf(nft("a", previews = listOf(NftPreview("500x500", "ipfs://cid")))))

        assertNull(provider.fetch(OWNER)?.single()?.imageUrl)
    }

    @Test
    fun fetch_untrustedStrings_areNormalised() = test {
        startKit(
            items = listOf(
                nft(
                    "a",
                    name = "Na‮me\nline",
                    description = "De\u0000sc",
                    collection = NftCollection("c", "Co​ll", "Ab\tout"),
                ),
            ),
        )

        val draft = provider.fetch(OWNER)?.single()

        assertEquals("Na me line", draft?.name)
        assertEquals("De sc", draft?.description)
        assertEquals("Co ll", draft?.collectionName)
        assertEquals("Ab out", draft?.collectionDescription)
    }

    @Test
    fun fetch_overlongStrings_areCapped() = test {
        val long = "x".repeat(5000)
        startKit(items = listOf(nft("a", name = long, description = long, collection = NftCollection("c", long, long))))

        val draft = provider.fetch(OWNER)?.single()

        assertEquals(128, draft?.name?.length)
        assertEquals(2000, draft?.description?.length)
        assertEquals(128, draft?.collectionName?.length)
        assertEquals(2000, draft?.collectionDescription?.length)
    }

    @Test
    fun fetch_blankAfterSanitising_becomesNull() = test {
        startKit(items = listOf(nft("a", name = " ‮\n ", description = "\u0000")))

        val draft = provider.fetch(OWNER)?.single()

        assertNull(draft?.name)
        assertNull(draft?.description)
    }

    @Test
    fun fetch_kitWithAnotherAddress_returnsNullWithoutRequest() = test {
        val other = startKit(address = OTHER_OWNER, items = listOf(nft("a")))

        assertNull(provider.fetch(OWNER))
        coVerify(exactly = 0) { other.nftItems(any()) }
    }

    @Test
    fun fetch_noStartedKit_returnsNull() = test {
        assertNull(provider.fetch(OWNER))
    }

    @Test
    fun fetch_secondVersionOwnsAddress_usesThatKit() = test {
        startKit(address = OTHER_OWNER, version = TonWalletVersion.V4R2, items = listOf(nft("v4")))
        startKit(address = OWNER, version = TonWalletVersion.W5, items = listOf(nft("w5")))

        assertEquals(listOf("w5"), provider.fetch(OWNER)?.map { it.address })
    }

    @Test
    fun expiringDomains_kitItems_mapsAddressNameExpiryAndSaleState() = test {
        startKit(
            items = emptyList(),
            domains = listOf(
                DnsExpiringItem(100, "free.ton", nft("free-nft")),
                DnsExpiringItem(200, "sold.ton", nft("sold-nft", isOnSale = true)),
            ),
        )

        assertEquals(
            listOf(NftDomainDraft("free-nft", "free.ton", 100, false), NftDomainDraft("sold-nft", "sold.ton", 200, true)),
            provider.expiringDomains(OWNER, 30),
        )
    }

    @Test
    fun expiringDomains_itemWithoutNft_isSkipped() = test {
        startKit(
            items = emptyList(),
            domains = listOf(DnsExpiringItem(100, "lost.ton", null), DnsExpiringItem(200, "kept.ton", nft("kept"))),
        )

        assertEquals(listOf("kept"), provider.expiringDomains(OWNER, 30)?.map { it.nftAddress })
    }

    @Test
    fun expiringDomains_untrustedName_isSanitizedAndBlankSkipped() = test {
        startKit(
            items = emptyList(),
            domains = listOf(
                DnsExpiringItem(100, "pi‮rate\n.ton", nft("a")),
                DnsExpiringItem(200, " ‮\n ", nft("b")),
            ),
        )

        assertEquals(listOf("pi rate .ton"), provider.expiringDomains(OWNER, 30)?.map { it.name })
    }

    @Test
    fun expiringDomains_periodDays_isPassedToKit() = test {
        val kit = startKit(items = emptyList())

        provider.expiringDomains(OWNER, 366)

        coVerify { kit.dnsExpiring(366) }
    }

    @Test
    fun expiringDomains_noStartedKit_returnsNull() = test {
        assertNull(provider.expiringDomains(OWNER, 30))
    }

    @Test
    fun ownerAddresses_walletsOfSameVersion_derivesOnce() = test {
        val account = mockk<Account>()
        coEvery { tonKitManager.getReceiveAddress(account, TonWalletVersion.V4R2) } returns OWNER
        coEvery { tonKitManager.getReceiveAddress(account, TonWalletVersion.W5) } returns OTHER_OWNER

        val result = provider.ownerAddresses(
            listOf(
                wallet(account, TokenType.Native),
                wallet(account, TokenType.Jetton("jetton")),
                wallet(account, TokenType.TonW5Native),
            ),
        )

        assertEquals(setOf(OWNER, OTHER_OWNER), result)
        coVerify(exactly = 1) { tonKitManager.getReceiveAddress(account, TonWalletVersion.V4R2) }
    }

    @Test
    fun ownerAddresses_onlyJettonWallets_hasNoOwner() = test {
        val account = mockk<Account>()

        val result = provider.ownerAddresses(
            listOf(wallet(account, TokenType.Jetton("jetton")), wallet(account, TokenType.TonW5Jetton("jetton"))),
        )

        assertEquals(emptySet<String>(), result)
        coVerify(exactly = 0) { tonKitManager.getReceiveAddress(any(), any()) }
    }

    @Test
    fun ownerAddresses_derivationThrows_skipsOnlyThatWallet() = test {
        val account = mockk<Account>()
        coEvery { tonKitManager.getReceiveAddress(account, TonWalletVersion.V4R2) } throws IllegalArgumentException()
        coEvery { tonKitManager.getReceiveAddress(account, TonWalletVersion.W5) } returns OWNER

        val result = provider.ownerAddresses(
            listOf(wallet(account, TokenType.Native), wallet(account, TokenType.TonW5Native)),
        )

        assertEquals(setOf(OWNER), result)
    }

    @Test
    fun ownerAddresses_derivationCancelled_rethrows() = test {
        val account = mockk<Account>()
        coEvery { tonKitManager.getReceiveAddress(account, TonWalletVersion.V4R2) } throws CancellationException()

        assertFailsWith<CancellationException> {
            provider.ownerAddresses(listOf(wallet(account, TokenType.Native)))
        }
    }

    @Test
    fun changes_matchingKitAppears_emitsKitAppeared() = test {
        val changes = collectChanges()
        assertTrue(changes.isEmpty())

        startKit(items = emptyList())

        assertEquals(listOf<NftChange>(NftChange.KitAppeared), changes)
    }

    @Test
    fun changes_kitOfAnotherAddress_emitsNothing() = test {
        val changes = collectChanges()

        startKit(address = OTHER_OWNER, items = emptyList())

        assertTrue(changes.isEmpty())
    }

    @Test
    fun changes_kitEvent_emitsNewEvent() = test {
        val events = MutableSharedFlow<EventInfo>()
        val changes = collectChanges()
        startKit(items = emptyList(), events = events)

        events.emit(mockk())

        assertEquals(listOf(NftChange.KitAppeared, NftChange.NewEvent), changes)
    }

    @Test
    fun changes_kitReplaced_resubscribesToNewKitEvents() = test {
        val oldEvents = MutableSharedFlow<EventInfo>()
        val newEvents = MutableSharedFlow<EventInfo>()
        val changes = collectChanges()
        startKit(items = emptyList(), events = oldEvents)

        startKit(items = emptyList(), events = newEvents)
        newEvents.emit(mockk())

        assertEquals(listOf(NftChange.KitAppeared, NftChange.KitAppeared, NftChange.NewEvent), changes)
        assertEquals(0, oldEvents.subscriptionCount.value)
    }

    @Test
    fun supportsSection_tonAssets_trueOnlyForNative() {
        assertTrue(provider.supportsSection(wallet(mockk(), TokenType.Native)))
        assertTrue(provider.supportsSection(wallet(mockk(), TokenType.TonW5Native)))
        assertFalse(provider.supportsSection(wallet(mockk(), TokenType.Jetton("jetton"))))
    }

    @Test
    fun liveState_metadataButtons_mapsLabelAndTrimmedUri() = test {
        givenLiveItem(buttons = listOf(NftButton("Claim", "  https://example.com/claim \n")))

        val state = provider.liveState(OWNER, NFT_ADDRESS)

        assertEquals(listOf(NftLinkButton("Claim", "https://example.com/claim")), state?.buttons)
    }

    @Test
    fun liveState_moreThanFiveButtons_keepsFirstFiveValidOnes() = test {
        val invalid = listOf(NftButton("‮ ", "https://a.com"), NftButton("Long", "https://a.com/" + "x".repeat(2048)))
        val valid = (1..6).map { NftButton("B$it", "https://a.com/$it") }
        givenLiveItem(buttons = invalid + valid)

        val state = provider.liveState(OWNER, NFT_ADDRESS)

        assertEquals(listOf("B1", "B2", "B3", "B4", "B5"), state?.buttons?.map { it.label })
    }

    @Test
    fun liveState_unsafeLongLabel_stripsControlCharsAndCapsLength() = test {
        givenLiveItem(buttons = listOf(NftButton("Get‮reward\n" + "x".repeat(100), "https://a.com")))

        val label = provider.liveState(OWNER, NFT_ADDRESS)?.buttons?.single()?.label

        assertEquals("Get reward " + "x".repeat(53), label)
    }

    private fun givenLiveItem(buttons: List<NftButton>) {
        val kit = startKit(items = emptyList())
        coEvery { kit.nftItem(Address.parse(NFT_ADDRESS)) } returns nft(NFT_ADDRESS, buttons = buttons)
    }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher, timeout = 10.seconds, testBody = block)

    private fun TestScope.collectChanges(): List<NftChange> {
        val changes = mutableListOf<NftChange>()
        backgroundScope.launch { provider.changes(OWNER).toList(changes) }
        return changes
    }

    private fun startKit(
        items: List<NftItem>,
        address: String = OWNER,
        version: TonWalletVersion = TonWalletVersion.V4R2,
        events: Flow<EventInfo> = emptyFlow(),
        domains: List<DnsExpiringItem> = emptyList(),
    ): TonKit {
        val kit = mockk<TonKit> {
            every { receiveAddress } returns Address.parse(address)
            coEvery { nftItems(any()) } returns items
            coEvery { dnsExpiring(any()) } returns domains
            every { eventFlow(TagQuery(null, null, null, null)) } returns events
        }
        kits.value = kits.value + (version to TonKitWrapper(kit, mockk(), version))
        return kit
    }

    private fun wallet(account: Account, type: TokenType): Wallet {
        val walletToken = mockk<Token> {
            every { tokenQuery } returns TokenQuery(BlockchainType.Ton, type)
        }
        return mockk {
            every { token } returns walletToken
            every { this@mockk.account } returns account
        }
    }

    private fun nft(
        address: String,
        name: String? = "Name",
        description: String? = "Description",
        renderType: String? = null,
        trust: KitNftTrust = KitNftTrust.WHITELIST,
        collection: NftCollection? = null,
        previews: List<NftPreview> = emptyList(),
        verified: Boolean = false,
        isOnSale: Boolean = false,
        buttons: List<NftButton> = emptyList(),
        lottie: String? = null,
    ) = NftItem(
        address = address,
        ownerAddress = null,
        name = name,
        description = description,
        renderType = renderType,
        trust = trust,
        verified = verified,
        collection = collection,
        previews = previews,
        isOnSale = isOnSale,
        dns = null,
        buttons = buttons,
        lottie = lottie,
    )

    private companion object {
        val OWNER: String = Address.parse("0:" + "ab".repeat(32)).toUserFriendly(false)
        val OTHER_OWNER: String = Address.parse("0:" + "cd".repeat(32)).toUserFriendly(false)
        val NFT_ADDRESS: String = Address.parse("0:" + "ef".repeat(32)).toUserFriendly(false)
    }
}
