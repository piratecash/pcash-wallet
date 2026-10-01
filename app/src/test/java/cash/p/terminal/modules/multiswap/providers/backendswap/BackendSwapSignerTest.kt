package cash.p.terminal.modules.multiswap.providers.backendswap

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.installEthereumCryptoProviderForTest
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.EvmSignerFactory
import cash.p.terminal.modules.multiswap.providers.backendswap.BackendSwapGoldenVectors.assertSameSignature
import cash.p.terminal.modules.multiswap.providers.backendswap.BackendSwapGoldenVectors.softwareAddress
import cash.p.terminal.modules.multiswap.providers.backendswap.BackendSwapGoldenVectors.softwareSigner
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.ethereumkit.models.Chain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.math.BigInteger

class BackendSwapSignerTest {

    private val dispatcher = StandardTestDispatcher()
    private val evmSignerFactory = mockk<EvmSignerFactory>()
    private val evmBlockchainManager = mockk<EvmBlockchainManager> {
        every { getChain(any()) } returns Chain.Ethereum
    }
    private val signer = BackendSwapSigner(
        evmSignerFactory,
        evmBlockchainManager,
        TestDispatcherProvider(dispatcher, TestScope(dispatcher)),
    )

    @Before
    fun setUp() {
        installEthereumCryptoProviderForTest()
    }

    @Test
    fun walletAddress_ethereumKey_returnsEip55AndSkipsOtherChains() = runTest(dispatcher) {
        val account = account(AccountType.EvmPrivateKey(BigInteger.ONE))
        coEvery { evmSignerFactory.resolveAddress(account, BlockchainType.Ethereum, any()) } returns softwareAddress()

        assertEquals(BackendSwapGoldenVectors.ADDRESS, signer.walletAddress(account))
        coVerify(exactly = 1) { evmSignerFactory.resolveAddress(any(), any(), any()) }
    }

    @Test
    fun walletAddress_noEthereumKey_fallsBackToAnotherEvmChain() = runTest(dispatcher) {
        val account = account(HARDWARE_CARD)
        coEvery { evmSignerFactory.resolveAddress(account, any(), any()) } returns null
        coEvery {
            evmSignerFactory.resolveAddress(account, BlockchainType.BinanceSmartChain, any())
        } returns softwareAddress()

        assertEquals(BackendSwapGoldenVectors.ADDRESS, signer.walletAddress(account))
    }

    @Test
    fun walletAddress_noEvmKey_returnsNull() = runTest(dispatcher) {
        val account = account(HARDWARE_CARD)
        coEvery { evmSignerFactory.resolveAddress(account, any(), any()) } returns null

        assertNull(signer.walletAddress(account))
    }

    @Test
    fun walletAddress_watchAccount_returnsNullWithoutResolving() = runTest(dispatcher) {
        assertNull(signer.walletAddress(account(AccountType.EvmAddress(BackendSwapGoldenVectors.ADDRESS))))
        coVerify(exactly = 0) { evmSignerFactory.resolveAddress(any(), any(), any()) }
    }

    @Test
    fun walletAddress_trezorBelowTypedDataMinimum_returnsNullWithoutResolving() = runTest(dispatcher) {
        assertNull(signer.walletAddress(account(trezor(firmwareVersion = "2.4.2"))))
        coVerify(exactly = 0) { evmSignerFactory.resolveAddress(any(), any(), any()) }
    }

    @Test
    fun walletAddress_trezorAtTypedDataMinimum_resolvesAddress() = runTest(dispatcher) {
        val account = account(trezor(firmwareVersion = "2.4.3"))
        coEvery { evmSignerFactory.resolveAddress(account, BlockchainType.Ethereum, any()) } returns softwareAddress()

        assertEquals(BackendSwapGoldenVectors.ADDRESS, signer.walletAddress(account))
    }

    @Test
    fun walletAddress_calledTwice_resolvesOnce() = runTest(dispatcher) {
        val account = account(AccountType.EvmPrivateKey(BigInteger.ONE))
        coEvery { evmSignerFactory.resolveAddress(account, BlockchainType.Ethereum, any()) } returns softwareAddress()

        signer.walletAddress(account)
        signer.walletAddress(account)

        coVerify(exactly = 1) { evmSignerFactory.resolveAddress(any(), any(), any()) }
    }

    @Test
    fun sign_softwareKeyOnFallbackChain_returnsEthersSignatureAsHex() = runTest(dispatcher) {
        val account = account(HARDWARE_CARD)
        coEvery { evmSignerFactory.resolveAddress(account, any(), any()) } returns null
        coEvery {
            evmSignerFactory.resolveAddress(account, BlockchainType.BinanceSmartChain, any())
        } returns softwareAddress()
        coEvery {
            evmSignerFactory.createSigner(account, BlockchainType.BinanceSmartChain, any())
        } returns softwareSigner()

        val signature = signer.sign(account, BackendSwapGoldenVectors.m1)

        assertSameSignature(BackendSwapGoldenVectors.M1_SIGNATURE, signature)
    }

    @Test(expected = IllegalStateException::class)
    fun sign_noEvmKey_throwsIllegalState() = runTest(dispatcher) {
        val account = account(HARDWARE_CARD)
        coEvery { evmSignerFactory.resolveAddress(account, any(), any()) } returns null

        signer.sign(account, BackendSwapGoldenVectors.m1)
    }

    private fun trezor(firmwareVersion: String) = AccountType.TrezorDevice(
        deviceId = "device-id",
        model = "T3T1",
        firmwareVersion = firmwareVersion,
        walletPublicKey = "wallet-key",
    )

    private fun account(type: AccountType) = Account(
        id = "account-id",
        name = "account",
        type = type,
        origin = AccountOrigin.Created,
        level = 0,
    )

    private companion object {
        val HARDWARE_CARD = AccountType.HardwareCard(
            cardId = "card-id",
            backupCardsCount = 0,
            walletPublicKey = "wallet-key",
            signedHashes = 0,
        )
    }
}
