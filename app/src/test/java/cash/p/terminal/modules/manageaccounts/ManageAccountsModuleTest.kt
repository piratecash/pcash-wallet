package cash.p.terminal.modules.manageaccounts

import android.os.Parcel
import cash.p.terminal.core.storage.AccountsDao
import cash.p.terminal.core.storage.AccountsStorage
import cash.p.terminal.core.storage.AppDatabase
import cash.p.terminal.modules.restoreaccount.MnemonicImportDraft
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.MnemonicDerivation
import cash.p.terminal.wallet.entities.AccountRecord
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ManageAccountsModuleTest {
    private val words = List(11) { "あいこくしん" } + "あおぞら"

    @Test
    fun input_parcelRoundtrip_retainsDecodedPolicyAndSelectedMode() {
        val draft = MnemonicImportDraft(text = words.joinToString(" "), passphrase = "páss",
            source = MnemonicImportDraft.Source.DecodedQr, decodedWords = words, derivation = MnemonicDerivation.Bip39)
        val parcel = Parcel.obtain()
        try {
            parcel.writeParcelable(ManageAccountsModule.Input(0, false, mnemonicDraft = draft), 0)
            parcel.setDataPosition(0)
            val restored = requireNotNull(parcel.readParcelable<ManageAccountsModule.Input>(javaClass.classLoader))
            val restoredDraft = requireNotNull(restored.mnemonicDraft)
            val legacy = restoredDraft.selectLegacy(true).accountType(false)
            assertEquals(words, legacy.words)
            assertEquals("páss", legacy.passphrase)
            assertTrue(AccountType.Mnemonic(words, "páss", MnemonicDerivation.Legacy).seed.contentEquals(legacy.seed))
            assertEquals(MnemonicDerivation.Bip39, restoredDraft.derivation)
        } finally { parcel.recycle() }
    }

    @Test
    fun storage_roundtrip_modesRemainDistinctAndUnknownTypeIsUnsupported() {
        val dao = mockk<AccountsDao>()
        val database = mockk<AppDatabase> { every { accountsDao() } returns dao }
        val storage = AccountsStorage(database)
        val record = slot<AccountRecord>()
        every { dao.insert(capture(record)) } returns Unit
        every { dao.loadAccount("fixture") } answers { record.captured }
        val types = MnemonicDerivation.entries.map { AccountType.Mnemonic(words, "  ", it) }
        assertEquals(2, types.toSet().size)
        assertNotEquals(types[0].hashCode(), types[1].hashCode())
        types.forEach { type ->
            storage.save(Account("fixture", type.derivation.name, type, AccountOrigin.Restored, 0))
            assertEquals(type.derivation.typeCode, record.captured.type)
            val restored = requireNotNull(storage.loadAccount("fixture")).type as AccountType.Mnemonic
            assertEquals(type, restored)
            assertTrue(type.seed.contentEquals(restored.seed))
            val parcel = Parcel.obtain()
            try {
                parcel.writeParcelable(type, 0)
                parcel.setDataPosition(0)
                val copied = requireNotNull(parcel.readParcelable<AccountType.Mnemonic>(javaClass.classLoader))
                assertTrue(type.seed.contentEquals(copied.seed))
            } finally { parcel.recycle() }
        }
        every { dao.loadAccount("fixture") } returns record.captured.copy(type = "mnemonic_future")
        assertNull(storage.loadAccount("fixture"))
    }
}
