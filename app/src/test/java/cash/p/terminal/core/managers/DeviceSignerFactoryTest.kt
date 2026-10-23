package cash.p.terminal.core.managers

import cash.p.terminal.core.UnsupportedAccountException
import cash.p.terminal.ledger.account.LedgerSolanaClearSigning
import cash.p.terminal.ledger.domain.LedgerAppAccess
import cash.p.terminal.ledgerkit.transport.LedgerTransport
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class DeviceSignerFactoryTest {

    private val ledger = AccountType.LedgerDevice("device", "NanoSPlus", "wallet-key")
    private val account = mockk<Account> {
        every { id } returns "account"
        every { type } returns ledger
    }
    private val device = mockk<LedgerTransport> {
        coEvery { exchange(any()) } returns ByteArray(SIGNATURE_SIZE) + byteArrayOf(0x90.toByte(), 0)
    }
    private val appAccess = mockk<LedgerAppAccess> {
        coEvery { signWithLedgerApp<Any>(account, any(), any()) } coAnswers {
            thirdArg<suspend (LedgerTransport) -> Any>().invoke(device)
        }
    }
    private val clearSigning = mockk<LedgerSolanaClearSigning>(relaxed = true)
    private val factory = DeviceSignerFactory(mockk(), appAccess, clearSigning)

    @Test
    fun solana_ledgerOfflineSigning_neverAsksForClearSigningData() {
        factory.solana(account, ledger, KEY, online = false).sign(MESSAGE)

        coVerify(exactly = 0) { clearSigning.prepare(any(), any(), any()) }
    }

    @Test
    fun solana_ledgerOnline_preparesClearSigningOnTheSigningSession() {
        factory.solana(account, ledger, KEY, online = true).sign(MESSAGE)

        coVerify { clearSigning.prepare(device, "NanoSPlus", MESSAGE) }
    }

    @Test
    fun thorchain_ledger_signerCarriesStoredCompressedKey() {
        val key = KEY.copyWith(publicKey = THORCHAIN_PUBLIC_KEY)

        assertContentEquals(THORCHAIN_PUBLIC_KEY, factory.thorchain(account, ledger, key).publicKey)
    }

    @Test
    fun tonAndThorchain_trezor_throwUnsupportedWithoutTouchingDevice() {
        val trezor = mockk<AccountType.TrezorDevice>()

        assertFailsWith<UnsupportedAccountException> { factory.ton(account, trezor, KEY) }
        assertFailsWith<UnsupportedAccountException> { factory.thorchain(account, trezor, KEY) }
        coVerify(exactly = 0) { appAccess.signWithLedgerApp<Any>(any(), any(), any()) }
    }

    private companion object {
        val THORCHAIN_PUBLIC_KEY = ByteArray(33) { 3 }

        fun HardwarePublicKey.copyWith(publicKey: ByteArray) = HardwarePublicKey(
            accountId = accountId,
            blockchainType = BlockchainType.Thorchain.uid,
            type = HardwarePublicKeyType.ADDRESS,
            tokenType = tokenType,
            key = key,
            derivationPath = "m/44'/931'/0'/0/0",
            publicKey = publicKey,
            derivedPublicKey = derivedPublicKey,
        )

        const val SIGNATURE_SIZE = 64
        val MESSAGE = byteArrayOf(1, 2, 3)
        val KEY = HardwarePublicKey(
            accountId = "account",
            blockchainType = BlockchainType.Solana.uid,
            type = HardwarePublicKeyType.PUBLIC_KEY,
            tokenType = TokenType.Native,
            key = SecretString("1572e52f2c5e7e66bc19ac6cd30b6d696ea799cfe6d784542ab7d8b7fe315b5c"),
            derivationPath = "m/44'/501'/0'",
            publicKey = byteArrayOf(),
            derivedPublicKey = byteArrayOf(),
        )
    }
}
