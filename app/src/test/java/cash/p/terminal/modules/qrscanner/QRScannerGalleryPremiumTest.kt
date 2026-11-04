package cash.p.terminal.modules.qrscanner

import android.app.Application
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.navigation3.runtime.NavBackStack
import cash.p.terminal.R
import cash.p.terminal.core.deeplink.DeeplinkParser
import cash.p.terminal.core.managers.TonConnectManager
import cash.p.terminal.modules.main.AndroidRecomposerNavHost
import cash.p.terminal.modules.main.PlainTestPage
import cash.p.terminal.modules.premium.about.AboutPremiumPage
import cash.p.terminal.modules.premium.about.AboutPremiumUiState
import cash.p.terminal.modules.premium.about.AboutPremiumViewModel
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.navigation.HSPage
import cash.p.terminal.navigation.QrScannerInput
import cash.p.terminal.premium.domain.usecase.CheckPremiumUseCase
import cash.p.terminal.premium.domain.usecase.PremiumType
import cash.p.terminal.tangem.domain.sdk.CardSdkConfigRepository
import io.horizontalsystems.core.IPinComponent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class QRScannerGalleryPremiumTest {

    private val registry = RecordingActivityResultRegistry()
    private val navigation = HSNavigation(
        NavBackStack<HSPage>(PlainTestPage(), QRScannerPage(QrScannerInput(title = "")))
    )
    private val aboutPremiumViewModel = mockk<AboutPremiumViewModel>(relaxed = true) {
        every { uiState } returns AboutPremiumUiState()
        every { uiEvents } returns emptyFlow()
    }
    private lateinit var host: AndroidRecomposerNavHost

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    viewModel { QRScannerViewModel(mockk()) }
                    viewModel { aboutPremiumViewModel }
                    single { mockk<DeeplinkParser>() }
                    single { mockk<TonConnectManager>(relaxed = true) }
                    single { mockk<CardSdkConfigRepository>(relaxed = true) }
                    single { mockk<IPinComponent>(relaxed = true) }
                    single { mockk<CheckPremiumUseCase> { every { getPremiumType() } returns PremiumType.NONE } }
                }
            )
        }
        host = AndroidRecomposerNavHost(
            navigation,
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = registry
            }
        )
    }

    @After
    fun tearDown() {
        host.close()
        stopKoin()
    }

    @Test
    fun chooseFromPhotos_premiumGrantedOnAboutPremium_launchesGalleryOfLiveComposition() {
        host.clickOnText(R.string.choose_from_photos)
        val aboutPremiumPage = navigation.backStack.last()
        assertTrue(aboutPremiumPage is AboutPremiumPage)

        navigation.setResult(aboutPremiumPage, AboutPremiumPage.Result())
        navigation.navigateUp()
        host.advanceFrames()

        assertEquals(listOf<Any?>("image/*"), registry.launchedInputs)
    }
}

/** A real registry: launching a launcher unregistered by a disposed composition throws, as in the app. */
private class RecordingActivityResultRegistry : ActivityResultRegistry() {
    val launchedInputs = mutableListOf<Any?>()

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        launchedInputs += input
    }
}
