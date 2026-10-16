package cash.p.terminal.premium.domain.usecase

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import cash.p.terminal.network.pirate.domain.enity.TrialPremiumResult
import cash.p.terminal.network.pirate.domain.repository.PiratePlaceRepository
import cash.p.terminal.premium.data.database.PremiumDatabase
import cash.p.terminal.premium.data.database.PremiumDatabaseConstructor
import cash.p.terminal.premium.data.database.PremiumDatabaseProvider
import cash.p.terminal.premium.data.database.StaticPremiumDatabaseProvider
import cash.p.terminal.premium.domain.HardwareBnbAddressProvider
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.IAccountManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals

class TrialPremiumPersistenceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val piratePlaceRepository = mockk<PiratePlaceRepository>()
    private val account = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.Mnemonic(words = List(11) { "abandon" } + "about", passphrase = ""),
        origin = AccountOrigin.Created,
        level = 0,
        isBackedUp = true
    )
    private val accountManager = mockk<IAccountManager> { every { account(account.id) } returns account }

    @Test
    fun checkTrialPremiumStatus_trialActivatedBeforeRestart_readsPersistedRowWithoutNetwork() = runTest {
        val dbFile = File(tempFolder.root, "premium.db")
        coEvery { piratePlaceRepository.activateTrialPremium(any()) } returns
                TrialPremiumResult.DemoActive(daysLeft = 7)

        val beforeRestart = openDatabase(dbFile)
        ActivateTrialPremiumUseCase(
            premiumDatabaseProvider = StaticPremiumDatabaseProvider(beforeRestart),
            piratePlaceRepository = piratePlaceRepository,
            accountManager = accountManager,
            getBnbAddressUseCase = bnbAddressUseCase(StaticPremiumDatabaseProvider(beforeRestart))
        ).activateTrialPremium(account.id)
        beforeRestart.close()

        val afterRestart = openDatabase(dbFile)
        try {
            val provider = StaticPremiumDatabaseProvider(afterRestart)
            val result = CheckTrialPremiumUseCase(
                piratePlaceRepository = piratePlaceRepository,
                premiumDatabaseProvider = provider,
                getBnbAddressUseCase = bnbAddressUseCase(provider),
                isDebug = false
            ).checkTrialPremiumStatus(account)

            assertEquals(TrialPremiumResult.DemoActive(daysLeft = 7), result)
            coVerify(exactly = 0) { piratePlaceRepository.checkTrialPremiumStatus(any()) }
        } finally {
            afterRestart.close()
        }
    }

    private fun bnbAddressUseCase(provider: PremiumDatabaseProvider) = GetBnbAddressUseCaseImpl(
        seedToEvmAddressUseCase = SeedToEvmAddressUseCase(),
        premiumDatabaseProvider = provider,
        hardwareBnbAddressProvider = mockk<HardwareBnbAddressProvider>()
    )

    private fun openDatabase(file: File): PremiumDatabase = Room.databaseBuilder<PremiumDatabase>(
        name = file.absolutePath,
        factory = PremiumDatabaseConstructor::initialize,
    )
        .setDriver(BundledSQLiteDriver())
        .build()
}
