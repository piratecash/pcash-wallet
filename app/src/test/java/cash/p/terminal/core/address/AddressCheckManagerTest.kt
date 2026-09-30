package cash.p.terminal.core.address

import cash.p.terminal.core.factories.ContractValidatorFactory
import cash.p.terminal.core.providers.AppConfigProvider
import cash.p.terminal.entities.Address
import cash.p.terminal.modules.send.address.AddressCheckResult
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AddressCheckManagerTest {
    private val checkerClasses = arrayOf(
        ContractAddressChecker::class, PhishingAddressChecker::class, BlacklistAddressChecker::class,
        AmlAddressChecker::class, SanctionAddressChecker::class,
    )
    private lateinit var checkers: List<AddressChecker>
    private lateinit var manager: AddressCheckManager

    @Before
    fun setUp() {
        mockkObject(AppConfigProvider, ContractValidatorFactory)
        every { AppConfigProvider.hashDitBaseUrl } returns "https://unused.invalid/"
        every { AppConfigProvider.hashDitApiKey } returns "test"
        every { AppConfigProvider.chainalysisBaseUrl } returns "https://unused.invalid/"
        every { AppConfigProvider.chainalysisApiKey } returns "test"
        every { ContractValidatorFactory.get(any()) } returns null
        mockkConstructor(*checkerClasses)
        manager = AddressCheckManager(mockk(), mockk(), mockk())
        val field = AddressCheckManager::class.java.getDeclaredField("checkers").apply { isAccessible = true }
        checkers = (field.get(manager) as Map<*, *>).values.map { it as AddressChecker }
        // Stub every screening operation so a regressed guard cannot make a network request.
        checkers.forEach { checker ->
            coEvery { checker.isClear(any(), any()) } returns AddressCheckResult.Detected
        }
    }

    @After
    fun tearDown() {
        unmockkConstructor(*checkerClasses)
        unmockkObject(AppConfigProvider, ContractValidatorFactory)
    }

    @Test
    fun availableCheckTypes_nativeBeam_returnsEmptyWithoutConsultingCheckers() {
        assertTrue(manager.availableCheckTypes(token(BlockchainType.Beam)).isEmpty())

        verify { checkers wasNot Called }
    }

    @Test
    fun isClear_nativeBeamEveryType_returnsNotAvailableBeforeCacheOrCheckers() = runTest {
        val beam = token(BlockchainType.Beam)
        val recipient = mockk<Address> { every { hex } returns "opaque-beam-receiver-test" }

        repeat(2) {
            AddressCheckType.entries.forEach { type ->
                assertEquals(AddressCheckResult.NotAvailable, manager.isClear(type, recipient, beam))
            }
        }

        verify { recipient wasNot Called }
        verify { checkers wasNot Called }
    }

    @Test
    fun availableCheckTypes_otherChains_preservesExistingSupportIncludingGamingBeam() {
        val ethereum = token(BlockchainType.Ethereum)
        every { ContractValidatorFactory.get(BlockchainType.Ethereum) } returns mockk()
        assertEquals(AddressCheckType.entries, manager.availableCheckTypes(ethereum))
        assertEquals(listOf(AddressCheckType.Sanction), manager.availableCheckTypes(token(BlockchainType.Bitcoin)))

        listOf(BlockchainType.Ethereum, BlockchainType.BinanceSmartChain).forEach { chain ->
            val native = token(chain)
            listOf(TokenType.Native, TokenType.Eip20("contract")).forEach { type ->
                val gaming = native.copy(coin = Coin("beam-2", "Beam", "BEAM"), type = type)
                assertEquals(manager.availableCheckTypes(native), manager.availableCheckTypes(gaming))
                assertTrue(AddressCheckType.Sanction in manager.availableCheckTypes(gaming))
            }
        }
        checkers.forEach { checker -> coVerify(exactly = 0) { checker.isClear(any(), any()) } }
    }

    @Test
    fun isClear_nonBeamEveryType_preservesCheckerResultsAndCache() = runTest {
        val ethereum = token(BlockchainType.Ethereum)
        val recipient = Address("test-address")

        repeat(2) {
            AddressCheckType.entries.forEach { type ->
                assertEquals(AddressCheckResult.Detected, manager.isClear(type, recipient, ethereum))
            }
        }

        checkers.forEach { checker -> coVerify(exactly = 1) { checker.isClear(recipient, ethereum) } }
    }

    private fun token(chain: BlockchainType) = Token(
        coin = Coin(chain.uid, chain.uid, chain.uid),
        blockchain = Blockchain(chain, chain.uid, null),
        type = TokenType.Native,
        decimals = if (chain == BlockchainType.Beam) 8 else 18,
    )
}
