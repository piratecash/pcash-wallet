package cash.p.terminal.tangem.domain.sdk

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import com.tangem.TangemSdk
import com.tangem.common.authentication.AuthenticationManager
import com.tangem.sdk.nfc.NfcManager
import com.tangem.sdk.nfc.NfcReader
import io.horizontalsystems.core.BackgroundManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CardSdkProviderTest {

    private val backgroundManager: BackgroundManager = mockk(relaxed = true)
    private val sdkInitializer: SdkInitializer = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        shadowOf(RuntimeEnvironment.getApplication().packageManager)
            .setSystemFeature(PackageManager.FEATURE_NFC, true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun register_calledTwice_callsOnStopAndOnDestroyOnPreviousNfcManager() {
        val activity1 = mockActivity()
        val activity2 = mockActivity()
        val components1 = mockComponents()
        val components2 = mockComponents()

        every { sdkInitializer.create(activity1) } returns components1
        every { sdkInitializer.create(activity2) } returns components2

        val provider = CardSdkProvider(backgroundManager, sdkInitializer)

        provider.register(activity1)
        provider.register(activity2)

        verify { components1.nfcManager.onStop(activity1) }
        verify { components1.nfcManager.onDestroy(activity1) }
    }

    @Test
    fun cancelSession_activeHolder_stopsActiveNfcSession() {
        val activity = mockActivity()
        val components = mockComponents()

        every { sdkInitializer.create(activity) } returns components

        val provider = CardSdkProvider(backgroundManager, sdkInitializer)
        provider.register(activity)

        val reader = components.nfcManager.reader
        provider.cancelSession()

        verify { reader.stopSession(cancelled = true) }
    }

    @Test
    fun observerOnDestroy_naturalDestroy_doesNotRemoveNfcManagerFromLifecycle() {
        val activity = mockActivity()
        val lifecycle = activity.lifecycle
        val components = mockComponents()
        val observerSlot = slot<LifecycleObserver>()

        every { lifecycle.addObserver(capture(observerSlot)) } returns Unit
        every { sdkInitializer.create(activity) } returns components

        val provider = CardSdkProvider(backgroundManager, sdkInitializer)
        provider.register(activity)

        val cardSdkObserver = observerSlot.captured as DefaultLifecycleObserver
        cardSdkObserver.onDestroy(activity)

        verify(exactly = 0) { lifecycle.removeObserver(components.nfcManager) }
    }

    @Test
    fun consumeNfcIntent_nfcDiscoveryActions_returnsTrue() {
        val provider = CardSdkProvider(backgroundManager, sdkInitializer)

        listOf(
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
        ).forEach { action ->
            assertTrue(action, provider.consumeNfcIntent(Intent(action)))
        }
    }

    @Test
    fun consumeNfcIntent_viewIntent_returnsFalse() {
        val provider = CardSdkProvider(backgroundManager, sdkInitializer)

        assertFalse(provider.consumeNfcIntent(Intent(Intent.ACTION_VIEW)))
    }

    @Test
    fun observerOnResume_registeredResumedActivity_enablesForegroundDispatch() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        every { sdkInitializer.create(activity) } returns mockComponents()

        CardSdkProvider(backgroundManager, sdkInitializer).register(activity)

        val shadow = shadowOf(NfcAdapter.getDefaultAdapter(activity))
        assertSame(activity, shadow.enabledActivity)
        assertNotNull(shadow.intent)
        assertNull(shadow.filters)
        assertNull(shadow.techLists)
    }

    @Test
    fun observerOnPause_registeredActivity_disablesForegroundDispatch() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val activity = controller.get()
        every { sdkInitializer.create(activity) } returns mockComponents()
        CardSdkProvider(backgroundManager, sdkInitializer).register(activity)

        controller.pause()

        val shadow = shadowOf(NfcAdapter.getDefaultAdapter(activity))
        assertSame(activity, shadow.disabledActivity)
    }

    private fun mockActivity(): FragmentActivity {
        val lifecycleMock: Lifecycle = mockk(relaxed = true)
        return mockk<FragmentActivity>(relaxed = true).apply {
            every { isDestroyed } returns false
            every { isFinishing } returns false
            every { isChangingConfigurations } returns false
            every { lifecycle } returns lifecycleMock
        }
    }

    private fun mockComponents(): SdkInitializer.Components {
        val nfcManager: NfcManager = mockk(relaxed = true)
        val authenticationManager: AuthenticationManager = mockk(relaxed = true)
        val sdk: TangemSdk = mockk(relaxed = true)
        val reader: NfcReader = mockk(relaxed = true)
        every { nfcManager.reader } returns reader
        return SdkInitializer.Components(
            nfcManager = nfcManager,
            authenticationManager = authenticationManager,
            sdk = sdk,
        )
    }
}
