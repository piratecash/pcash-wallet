package cash.p.terminal.modules.settings.appstatus

import android.net.Uri
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.KoinLogsDao
import cash.p.terminal.core.TestDispatcherProvider
import cash.p.terminal.core.managers.BtcBlockchainManager
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.managers.MoneroKitManager
import cash.p.terminal.core.managers.NetworkErrorTracker
import cash.p.terminal.core.managers.SolanaKitManager
import cash.p.terminal.core.managers.StellarKitManager
import cash.p.terminal.core.managers.ThorchainKitManagers
import cash.p.terminal.core.managers.TonKitManager
import cash.p.terminal.core.managers.TronKitManager
import cash.p.terminal.feature.miniapp.domain.storage.IUniqueCodeStorage
import cash.p.terminal.modules.blockchainstatus.StatusReportFile
import cash.p.terminal.modules.blockchainstatus.TestCoreApp
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.wallet.IAccountManager
import cash.p.terminal.wallet.IAdapterManager
import cash.p.terminal.wallet.IWalletManager
import cash.p.terminal.wallet.MarketKitWrapper
import io.horizontalsystems.core.CoreApp
import io.horizontalsystems.core.ISystemInfoManager
import io.horizontalsystems.core.storage.LogsDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

/**
 * Every manager/kit dependency is a relaxed mock: list/map-returning properties default to
 * empty, so none of the real per-blockchain status providers touch kit internals during init.
 * Robolectric is required for [android.os.Build] fields, a real ApplicationInfo, and the locale
 * data that init touches via reportBuildInfo()/DateHelper.formatDate()/getDeviceClass().
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestCoreApp::class)
class AppStatusViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val logsDao = mockk<LogsDao>(relaxed = true)
    private lateinit var koinLogsDao: KoinLogsDao
    private val statusReportFile = mockk<StatusReportFile>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        koinLogsDao = KoinLogsDao(logsDao)
        CoreApp.instance = RuntimeEnvironment.getApplication() as CoreApp
        // Robolectric leaves this field unset; reportBuildInfo() requires a non-null value.
        CoreApp.instance.applicationInfo.nativeLibraryDir = "/data/app/x86_64"
    }

    @After
    fun tearDown() {
        koinLogsDao.close()
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        accountManager: IAccountManager = mockk(relaxed = true),
        io: CoroutineDispatcher = dispatcher,
    ): AppStatusViewModel = AppStatusViewModel(
        context = CoreApp.instance,
        systemInfoManager = mockk<ISystemInfoManager>(relaxed = true),
        localStorage = mockk<ILocalStorage>(relaxed = true),
        uniqueCodeStorage = mockk<IUniqueCodeStorage>(relaxed = true),
        accountManager = accountManager,
        walletManager = mockk<IWalletManager>(relaxed = true),
        adapterManager = mockk<IAdapterManager>(relaxed = true),
        marketKit = mockk<MarketKitWrapper>(relaxed = true),
        evmBlockchainManager = mockk<EvmBlockchainManager>(relaxed = true),
        moneroKitManager = mockk<MoneroKitManager>(relaxed = true),
        stellarKitManager = mockk<StellarKitManager>(relaxed = true),
        thorchainKitManagers = mockk<ThorchainKitManagers>(relaxed = true),
        tronKitManager = mockk<TronKitManager>(relaxed = true),
        tonKitManager = mockk<TonKitManager>(relaxed = true),
        solanaKitManager = mockk<SolanaKitManager>(relaxed = true),
        btcBlockchainManager = mockk<BtcBlockchainManager>(relaxed = true),
        checkPremiumUseCase = mockk<CheckPremiumUseCase>(relaxed = true),
        networkErrorTracker = mockk<NetworkErrorTracker>(relaxed = true),
        dispatcherProvider = TestDispatcherProvider(dispatcher, TestScope(dispatcher), io),
        statusReportFile = statusReportFile,
    )

    /** AppLog.getLog() switches to the real Dispatchers.IO, so init completion needs a real-time wait. */
    private fun AppStatusViewModel.awaitInitLoaded() {
        runBlocking { withTimeout(30_000) { uiState.first { !it.loading } } }
    }

    @Test
    fun constructor_initCompletes_neverExportsOrStreamsTheFullLog() {
        val viewModel = createViewModel()

        viewModel.awaitInitLoaded()
        assertNotNull(viewModel.uiState.value.appStatusAsText)

        verify(exactly = 0) { logsDao.getGroupedPage(any(), any(), any()) }
        verify(exactly = 0) { logsDao.getGroupedPageByTag(any(), any(), any(), any()) }
        coVerify(exactly = 0) { statusReportFile.share(any(), any(), any(), any()) }
        coVerify(exactly = 0) { statusReportFile.write(any(), any(), any(), any()) }
    }

    @Test
    fun prepareShareUri_calledWhileFirstExportInProgress_returnsNullAndKeepsFlagUntilFirstCompletes() =
        runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.awaitInitLoaded()

        val shareUri = mockk<Uri>()
        val resumeExport = CompletableDeferred<Uri>()
        coEvery { statusReportFile.share(any(), any(), any(), any()) } coAnswers { resumeExport.await() }

        val firstCall = async { viewModel.prepareShareUri() }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.shareInProgress)

        val secondResult = viewModel.prepareShareUri()

        assertNull(secondResult)
        assertTrue(viewModel.uiState.value.shareInProgress)
        coVerify(exactly = 1) { statusReportFile.share(any(), any(), any(), any()) }

        resumeExport.complete(shareUri)
        assertEquals(shareUri, firstCall.await())
        assertFalse(viewModel.uiState.value.shareInProgress)
    }

    @Test
    fun prepareShareUri_calledFromCallerThread_buildsReportSectionsOnIo() {
        val ioExecutor = Executors.newSingleThreadExecutor { Thread(it, "share-io") }
        val accountReadThreads = mutableListOf<String>()
        val accountManager = mockk<IAccountManager>(relaxed = true)
        every { accountManager.accounts } answers {
            accountReadThreads += Thread.currentThread().name
            emptyList()
        }
        try {
            val viewModel = createViewModel(accountManager, ioExecutor.asCoroutineDispatcher())
            viewModel.awaitInitLoaded()
            accountReadThreads.clear()

            runBlocking { viewModel.prepareShareUri() }

            assertTrue(accountReadThreads.isNotEmpty())
            assertTrue(accountReadThreads.toString(), accountReadThreads.all { it.startsWith("share-io") })
        } finally {
            ioExecutor.shutdown()
        }
    }
}
