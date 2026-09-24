package cash.p.terminal.core.factories

import cash.p.terminal.core.UnsupportedException
import cash.p.terminal.core.adapters.zcash.TrezorZcashSigner
import cash.p.terminal.core.adapters.zcash.ZcashSpendingKeySigner
import cash.p.terminal.trezor.domain.TrezorAccountIdentityValidator
import cash.p.terminal.trezor.domain.TrezorFirmwareVersionRecorder
import cash.p.terminal.trezorkit.client.ITrezorClient
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Locks [AdapterFactory.buildZcashSigner]'s account-type based selection: a Trezor account signs
 * on the device, everything else keeps today's spending-key behaviour.
 */
class AdapterFactoryZcashSignerBuildTest {

    private val trezorClient = mockk<ITrezorClient>(relaxed = true)
    private val identityValidator = mockk<TrezorAccountIdentityValidator>(relaxed = true)
    private val firmwareVersionRecorder = mockk<TrezorFirmwareVersionRecorder>(relaxed = true)

    @Before
    fun setUp() {
        stopKoin()
        startKoin {
            modules(module {
                single<ITrezorClient> { trezorClient }
                single<TrezorAccountIdentityValidator> { identityValidator }
                single<TrezorFirmwareVersionRecorder> { firmwareVersionRecorder }
            })
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun buildZcashSigner_trezorAccount_returnsTrezorZcashSigner() {
        val accountType = AccountType.TrezorDevice(
            deviceId = "device-1",
            model = "T2B1",
            firmwareVersion = "2.6.0",
            walletPublicKey = "public-key",
        )
        val account = mockk<Account> {
            every { id } returns "account-1"
            every { type } returns accountType
        }
        val hardwarePublicKey = HardwarePublicKey(
            accountId = "account-1",
            blockchainType = BlockchainType.Zcash.uid,
            type = HardwarePublicKeyType.PUBLIC_KEY,
            tokenType = TokenType.AddressSpecTyped(TokenType.AddressSpecType.Transparent),
            key = SecretString("key"),
            derivationPath = "m/44'/133'/0'",
            publicKey = byteArrayOf(1, 2, 3),
            derivedPublicKey = byteArrayOf(4, 5, 6),
        )
        val wallet = mockk<Wallet> {
            every { this@mockk.account } returns account
            every { this@mockk.hardwarePublicKey } returns hardwarePublicKey
        }

        val signer = AdapterFactory.buildZcashSigner(wallet)

        assertTrue("Expected TrezorZcashSigner, got $signer", signer is TrezorZcashSigner)
    }

    @Test
    fun buildZcashSigner_trezorAccountWithoutHardwareKey_reportsTheMissingKey() {
        val account = mockk<Account> {
            every { id } returns "account-3"
            every { type } returns AccountType.TrezorDevice(
                deviceId = "device-1",
                model = "T2B1",
                firmwareVersion = "2.6.0",
                walletPublicKey = "public-key",
            )
        }
        val wallet = mockk<Wallet> {
            every { this@mockk.account } returns account
            every { this@mockk.hardwarePublicKey } returns null
        }

        val failure = assertThrows(UnsupportedException::class.java) {
            AdapterFactory.buildZcashSigner(wallet)
        }

        assertEquals("Trezor does not have a key for Zcash", failure.message)
    }

    @Test
    fun buildZcashSigner_zCashUfvKeyAccount_returnsZcashSpendingKeySigner() {
        val account = mockk<Account> {
            every { id } returns "account-2"
            every { type } returns AccountType.ZCashUfvKey("uview1somekey")
        }
        val wallet = mockk<Wallet> {
            every { this@mockk.account } returns account
            every { this@mockk.hardwarePublicKey } returns null
        }

        val signer = AdapterFactory.buildZcashSigner(wallet)

        assertTrue("Expected ZcashSpendingKeySigner, got $signer", signer is ZcashSpendingKeySigner)
    }
}
