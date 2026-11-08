package cash.p.terminal.premium.domain.usecase

import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.premium.data.dao.PremiumUserDao
import cash.p.terminal.premium.data.database.PremiumDatabase
import cash.p.terminal.premium.data.database.SinglePremiumDatabaseRouter
import cash.p.terminal.premium.data.database.StaticPremiumDatabaseProvider
import cash.p.terminal.premium.data.repository.PremiumUserRepository
import cash.p.terminal.wallet.managers.UserManager
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PremiumLevelBelowRootTest {

    private val premiumUserDao = mockk<PremiumUserDao>(relaxUnitFun = true) {
        coEvery { getByLevel(any()) } returns null
    }
    private val database = mockk<PremiumDatabase> {
        every { premiumUserDao() } returns premiumUserDao
        every { demoPremiumUserDao() } returns mockk(relaxed = true)
        every { accountPremiumCacheDao() } returns mockk(relaxed = true) {
            coEvery { getAll() } returns emptyList()
        }
    }
    private val router = SinglePremiumDatabaseRouter(StaticPremiumDatabaseProvider(database))
    private val previousLogWriters = Logger.config.logWriterList
    private val warnings = mutableListOf<String>()

    @Before
    fun setUp() {
        Logger.setLogWriters(object : LogWriter() {
            override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
                if (severity == Severity.Warn) warnings += message
            }
        })
    }

    @After
    fun tearDown() {
        Logger.setLogWriters(previousLogWriters)
    }

    @Test
    fun forLevel_belowRoot_returnsTheRootDatabaseAndWarns() {
        assertSame(database, router.forLevel(router.rootLevel - 1))

        assertEquals(1, warnings.size)
    }

    @Test
    fun forLevel_atOrAboveRoot_doesNotWarn() {
        router.forLevel(router.rootLevel)
        router.forLevel(router.rootLevel + 1)

        assertTrue(warnings.isEmpty())
    }

    @Test
    fun update_levelBelowRoot_readsItFromTheRootDatabaseWithoutFailing() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val useCase = CheckPremiumUseCaseImpl(
            premiumUserRepository = PremiumUserRepository(router),
            premiumDatabaseRouter = router,
            binanceApi = mockk(relaxed = true),
            piratePlaceRepository = mockk(relaxed = true),
            accountManager = mockk(relaxed = true) {
                every { accounts } returns emptyList()
                every { activeAccount } returns null
                every { getDeletedAccountIds() } returns emptyList()
            },
            checkAdapterPremiumBalanceUseCase = mockk { coEvery { this@mockk.invoke() } returns null },
            checkTrialPremiumUseCase = mockk(relaxed = true),
            activateTrialPremiumUseCase = mockk(relaxed = true),
            getBnbAddressUseCase = mockk(relaxed = true),
            userManager = mockk<UserManager> { every { currentUserLevelFlow } returns MutableStateFlow(-1) },
            uptimeProvider = mockk(relaxed = true),
            dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher)),
        )

        assertEquals(PremiumType.NONE, useCase.update())

        coVerify(exactly = 1) { premiumUserDao.getByLevel(-1) }
        assertTrue(warnings.isNotEmpty())
    }
}
