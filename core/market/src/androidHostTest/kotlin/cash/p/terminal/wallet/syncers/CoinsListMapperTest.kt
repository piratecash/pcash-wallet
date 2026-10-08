package cash.p.terminal.wallet.syncers

import cash.p.terminal.network.pirate.domain.model.CoinsList
import cash.p.terminal.network.pirate.domain.model.RemoteBlockchain
import cash.p.terminal.network.pirate.domain.model.RemoteCoin
import cash.p.terminal.network.pirate.domain.model.RemoteToken
import cash.p.terminal.wallet.managers.VirtualCoinMapper
import cash.p.terminal.wallet.storage.BlockchainRecord
import cash.p.terminal.wallet.storage.TokenRecord
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoinsListMapperTest {

    private val mapper = CoinsListMapper()
    private val virtualCoinMapper = VirtualCoinMapper()

    private fun remoteCoin(
        uid: String,
        code: String,
        priority: Int? = null,
        tokens: List<RemoteToken> = emptyList(),
    ) = RemoteCoin(coingeckoId = uid, name = code, code = code, priority = priority, tokens = tokens)

    private fun remoteToken(
        blockchainUid: String,
        type: String = "native",
        decimals: Int? = 18,
        address: String? = null,
    ) = RemoteToken(type = type, blockchainUid = blockchainUid, address = address, decimals = decimals)

    private fun createToken(
        coinUid: String,
        blockchainUid: String,
        type: String = "native",
        decimals: Int = 18,
        reference: String = ""
    ) = TokenRecord(coinUid, blockchainUid, type, decimals, reference)

    @Test
    fun map_nestedTokens_flattenedWithCoinUidEqualCoingeckoId() {
        val list = CoinsList(
            blockchains = listOf(RemoteBlockchain("ethereum", "Ethereum", null)),
            coins = listOf(
                remoteCoin(
                    "yearn-finance", "yfi",
                    tokens = listOf(remoteToken("ethereum", type = "eip20", address = "0xabc", decimals = 18))
                )
            )
        )

        val data = mapper.map(list, emptyMap(), virtualCoinMapper)

        assertEquals(1, data.tokens.size)
        assertEquals("yearn-finance", data.tokens.single().coinUid)
    }

    @Test
    fun map_ranks_appliedAndMissingIsNull() {
        val list = CoinsList(
            blockchains = emptyList(),
            coins = listOf(remoteCoin("bitcoin", "btc"), remoteCoin("litecoin", "ltc"))
        )

        val data = mapper.map(list, mapOf("bitcoin" to 1), virtualCoinMapper)

        assertEquals(1, data.coins.first { it.uid == "bitcoin" }.marketCapRank)
        assertEquals(null, data.coins.first { it.uid == "litecoin" }.marketCapRank)
    }

    @Test
    fun map_coinWithoutTokens_keptAsCoin() {
        val list = CoinsList(blockchains = emptyList(), coins = listOf(remoteCoin("bitcoin", "btc")))

        val data = mapper.map(list, emptyMap(), virtualCoinMapper)

        assertEquals(1, data.coins.size)
        assertTrue(data.tokens.isEmpty())
    }

    // region injectVirtualTokens tests

    @Test
    fun injectVirtualTokens_bscUsdtCoinByUid_addsTetherBscToken() {
        val coinUids = setOf("tether", "binance-bridged-usdt-bnb-smart-chain")
        val tokens = listOf(createToken("binance-bridged-usdt-bnb-smart-chain", "binance-smart-chain"))

        val result = mapper.injectVirtualTokens(coinUids, tokens, virtualCoinMapper)

        assertEquals(2, result.size)
        assertTrue(
            result.any {
                it.coinUid == "binance-bridged-usdt-bnb-smart-chain" && it.blockchainUid == "binance-smart-chain"
            }
        )
        assertTrue(result.any { it.coinUid == "tether" && it.blockchainUid == "binance-smart-chain" })
    }

    @Test
    fun injectVirtualTokens_missingTetherCoin_returnsOriginalTokens() {
        val coinUids = setOf("binance-bridged-usdt-bnb-smart-chain")
        val tokens = listOf(createToken("binance-bridged-usdt-bnb-smart-chain", "binance-smart-chain"))

        val result = mapper.injectVirtualTokens(coinUids, tokens, virtualCoinMapper)

        assertEquals(1, result.size)
        assertEquals("binance-bridged-usdt-bnb-smart-chain", result[0].coinUid)
    }

    @Test
    fun injectVirtualTokens_missingRealCoinToken_returnsOriginalTokens() {
        val coinUids = setOf("tether")
        val tokens = listOf(createToken("some-token", "binance-smart-chain"))

        val result = mapper.injectVirtualTokens(coinUids, tokens, virtualCoinMapper)

        assertEquals(1, result.size)
        assertEquals("some-token", result[0].coinUid)
    }

    @Test
    fun injectVirtualTokens_realCoinTokenOnWrongBlockchain_returnsOriginalTokens() {
        val coinUids = setOf("tether", "binance-bridged-usdt-bnb-smart-chain")
        val tokens = listOf(createToken("binance-bridged-usdt-bnb-smart-chain", "ethereum"))

        val result = mapper.injectVirtualTokens(coinUids, tokens, virtualCoinMapper)

        assertEquals(1, result.size)
        assertEquals("binance-bridged-usdt-bnb-smart-chain", result[0].coinUid)
    }

    @Test
    fun injectVirtualTokens_emptyCoins_returnsOriginalTokens() {
        val tokens = listOf(createToken("binance-bridged-usdt-bnb-smart-chain", "binance-smart-chain"))

        val result = mapper.injectVirtualTokens(emptySet(), tokens, virtualCoinMapper)

        assertEquals(tokens, result)
    }

    @Test
    fun injectVirtualTokens_emptyTokens_returnsEmptyList() {
        val coinUids = setOf("tether", "binance-bridged-usdt-bnb-smart-chain")

        val result = mapper.injectVirtualTokens(coinUids, emptyList(), virtualCoinMapper)

        assertTrue(result.isEmpty())
    }

    // endregion

    // region transform / filterValidTokens tests (migrated from CoinResponseMapper)

    @Test
    fun transform_litecoinNativeToken_createsDerivedTokens() {
        val result = mapper.transform(
            listOf(createToken(coinUid = "litecoin", blockchainUid = "litecoin", decimals = 8))
        )

        assertEquals(
            listOf(
                "derived" to "Bip44",
                "derived" to "Bip49",
                "derived" to "Bip84",
                "derived" to "Bip86"
            ),
            result.map { it.type to it.reference }
        )
        assertTrue(result.all { it.coinUid == "litecoin" && it.blockchainUid == "litecoin" && it.decimals == 8 })
    }

    @Test
    fun transform_litecoinNativeAndMwebTokens_preservesMwebToken() {
        val result = mapper.transform(
            listOf(
                createToken(coinUid = "litecoin", blockchainUid = "litecoin", decimals = 8),
                createToken(coinUid = "litecoin", blockchainUid = "litecoin", type = "mweb", decimals = 8)
            )
        )

        assertEquals(
            1,
            result.count { it.coinUid == "litecoin" && it.blockchainUid == "litecoin" && it.type == "mweb" }
        )
        assertEquals(
            4,
            result.count { it.coinUid == "litecoin" && it.blockchainUid == "litecoin" && it.type == "derived" }
        )
    }

    @Test
    fun filterValidTokens_validBlockchainUid_retainsToken() {
        val blockchains = listOf(
            BlockchainRecord(uid = "ethereum", name = "Ethereum", eip3091url = null),
            BlockchainRecord(uid = "bitcoin", name = "Bitcoin", eip3091url = null)
        )
        val tokens = listOf(
            createToken(coinUid = "eth", blockchainUid = "ethereum"),
            createToken(coinUid = "btc", blockchainUid = "bitcoin")
        )

        val result = mapper.filterValidTokens(tokens, blockchains)

        assertEquals(2, result.size)
        assertEquals("eth", result[0].coinUid)
        assertEquals("btc", result[1].coinUid)
    }

    @Test
    fun filterValidTokens_invalidBlockchainUid_filtersOutToken() {
        val blockchains = listOf(BlockchainRecord(uid = "ethereum", name = "Ethereum", eip3091url = null))
        val tokens = listOf(
            createToken(coinUid = "eth", blockchainUid = "ethereum"),
            createToken(coinUid = "canton-token", blockchainUid = "canton-network")
        )

        val result = mapper.filterValidTokens(tokens, blockchains)

        assertEquals(1, result.size)
        assertEquals("eth", result[0].coinUid)
    }

    @Test
    fun filterValidTokens_emptyBlockchains_filtersOutAllTokens() {
        val tokens = listOf(
            createToken(coinUid = "eth", blockchainUid = "ethereum"),
            createToken(coinUid = "btc", blockchainUid = "bitcoin")
        )

        val result = mapper.filterValidTokens(tokens, emptyList())

        assertTrue(result.isEmpty())
    }

    @Test
    fun filterValidTokens_emptyTokens_returnsEmptyList() {
        val blockchains = listOf(BlockchainRecord(uid = "ethereum", name = "Ethereum", eip3091url = null))

        val result = mapper.filterValidTokens(emptyList(), blockchains)

        assertTrue(result.isEmpty())
    }

    @Test
    fun filterValidTokens_mixedValidAndInvalidTokens_retainsOnlyValid() {
        val blockchains = listOf(
            BlockchainRecord(uid = "ethereum", name = "Ethereum", eip3091url = null),
            BlockchainRecord(uid = "binance-smart-chain", name = "BSC", eip3091url = null)
        )
        val tokens = listOf(
            createToken(coinUid = "eth", blockchainUid = "ethereum"),
            createToken(coinUid = "orphan1", blockchainUid = "canton-network"),
            createToken(coinUid = "bnb", blockchainUid = "binance-smart-chain"),
            createToken(coinUid = "orphan2", blockchainUid = "unknown-chain")
        )

        val result = mapper.filterValidTokens(tokens, blockchains)

        assertEquals(2, result.size)
        assertEquals("eth", result[0].coinUid)
        assertEquals("bnb", result[1].coinUid)
    }

    // endregion

    // region mapFetched dedup tests (migrated)

    @Test
    fun map_duplicatePrimaryKeyTokenRows_keepsLastRow() {
        val list = CoinsList(
            blockchains = listOf(RemoteBlockchain("solana", "Solana", null)),
            coins = listOf(
                remoteCoin(
                    "dogwifcoin", "wif",
                    tokens = listOf(
                        remoteToken("solana", type = "spl", address = "EKpQ", decimals = null),
                        remoteToken("solana", type = "spl", address = "EKpQ", decimals = 6),
                    )
                )
            )
        )

        val data = mapper.map(list, emptyMap(), virtualCoinMapper)

        assertEquals(1, data.tokens.size)
        assertEquals(6, data.tokens.single().decimals)
    }

    @Test
    fun tokenPipeline_duplicatePrimaryKeyRows_preservesDuplicates() {
        val remoteCoins = listOf(
            remoteCoin(
                "dogwifcoin", "wif",
                tokens = listOf(
                    remoteToken("solana", type = "spl", address = "EKpQ", decimals = null),
                    remoteToken("solana", type = "spl", address = "EKpQ", decimals = 6),
                )
            )
        )
        val blockchains = listOf(BlockchainRecord(uid = "solana", name = "Solana", eip3091url = null))

        // Pre-dedup view relied on by the generator's collision-provenance guard.
        val result = mapper.tokenPipeline(remoteCoins, blockchains, virtualCoinMapper)

        assertEquals(2, result.size)
    }

    @Test
    fun map_duplicateNativeRowsOnTransformedChain_matchesLiveDatabaseState() {
        val list = CoinsList(
            blockchains = listOf(RemoteBlockchain("litecoin", "Litecoin", null)),
            coins = listOf(
                remoteCoin(
                    "litecoin", "ltc",
                    tokens = listOf(
                        remoteToken("litecoin", type = "native", decimals = null),
                        remoteToken("litecoin", type = "native", decimals = 8),
                    )
                )
            )
        )

        val data = mapper.map(list, emptyMap(), virtualCoinMapper)

        // Live behavior: transform consumes the first native row (null decimals) into the
        // derived rows; the second native row survives with its own decimals.
        val derived = data.tokens.filter { it.type == "derived" }
        assertEquals(listOf("Bip44", "Bip49", "Bip84", "Bip86"), derived.map { it.reference })
        assertTrue(derived.all { it.decimals == null })
        assertEquals(8, data.tokens.single { it.type == "native" }.decimals)
        assertEquals(5, data.tokens.size)
    }

    // endregion
}
