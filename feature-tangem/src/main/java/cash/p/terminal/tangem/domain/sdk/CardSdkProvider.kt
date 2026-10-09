package cash.p.terminal.tangem.domain.sdk

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import co.touchlab.kermit.Logger
import com.tangem.Log
import com.tangem.TangemSdk
import com.tangem.common.authentication.AuthenticationManager
import com.tangem.sdk.extensions.unsubscribe
import com.tangem.sdk.nfc.NfcManager
import io.horizontalsystems.core.BackgroundManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class CardSdkProvider(
    private val backgroundManager: BackgroundManager,
    private val sdkInitializer: SdkInitializer = DefaultSdkInitializer,
) {

    private val observer = Observer()

    private var holder: Holder? = null

    val sdk: TangemSdk
        get() = holder?.sdk ?: tryToRegisterWithForegroundActivity()

    fun register(activity: FragmentActivity) = runBlocking(Dispatchers.Main.immediate) {
        if (activity.isDestroyed || activity.isFinishing || activity.isChangingConfigurations) {
            val message =
                "Tangem SDK owner registration skipped: activity is destroyed or finishing"
            Log.info { message }
            return@runBlocking
        }

        if (holder != null) {
            unsubscribeAndCleanup()
        }

        initialize(activity)

        activity.lifecycle.addObserver(observer)

        Log.info { "Tangem SDK owner registered" }
    }

    /** Foreground-dispatch fallback: the OS re-applies reader mode around this delivery, so the tag itself is not used. */
    fun consumeNfcIntent(intent: Intent): Boolean {
        if (intent.action !in NFC_ACTIONS) return false
        logger.i { "NFC tag reached foreground dispatch: reader mode was dropped and is re-applied on resume" }
        return true
    }

    fun cancelSession() {
        holder?.nfcManager?.reader?.stopSession(cancelled = true)
    }

    private fun tryToRegisterWithForegroundActivity(): TangemSdk =
        runBlocking(Dispatchers.Main.immediate) {
            val warning =
                "Tangem SDK holder is null, trying to recreate it with foreground activity"
            Log.warning { warning }

            val activity = backgroundManager.currentActivity

            if (activity == null) {
                val error = "Tangem SDK holder is null and foreground activity is null"
                Log.error { error }
                error(error)
            }

            register(activity)

            val sdk = holder?.sdk

            if (sdk == null) {
                val error = "Tangem SDK is null after re-registering with foreground activity"
                Log.error { error }
                error(error)
            }

            return@runBlocking sdk
        }

    private fun initialize(activity: FragmentActivity) {
        val components = sdkInitializer.create(activity)

        holder = Holder(
            activity = activity,
            nfcManager = components.nfcManager,
            authenticationManager = components.authenticationManager,
            sdk = components.sdk,
        )

        Log.info { "Tangem SDK initialized" }
    }

    private fun unsubscribeAndCleanup() {
        val currentHolder = holder

        if (currentHolder == null) {
            Log.info { "Tangem SDK already unsubscribed and cleaned up" }
            return
        }

        with(currentHolder) {
            nfcManager.onStop(activity)
            nfcManager.onDestroy(activity)
            nfcManager.unsubscribe(activity)
            authenticationManager.unsubscribe(activity)

            activity.lifecycle.removeObserver(observer)
        }

        holder = null

        Log.info { "Tangem SDK unsubscribed and cleaned up" }
    }

    inner class Observer : DefaultLifecycleObserver {

        override fun onResume(owner: LifecycleOwner) {
            val activity = holder?.activity ?: return
            if (activity !== owner) return

            // Bare intent and FLAG_MUTABLE: the system fills in the NFC action and data.
            val pendingIntent = PendingIntent.getActivity(
                activity,
                0,
                Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_MUTABLE
            )
            NfcAdapter.getDefaultAdapter(activity)
                ?.enableForegroundDispatch(activity, pendingIntent, null, null)
        }

        override fun onPause(owner: LifecycleOwner) {
            val activity = holder?.activity ?: return
            if (activity !== owner) return

            NfcAdapter.getDefaultAdapter(activity)?.disableForegroundDispatch(activity)
        }

        override fun onDestroy(owner: LifecycleOwner) {
            Log.info { "Tangem SDK owner destroyed" }

            val currentHolder = holder ?: return
            if (currentHolder.activity !== owner) return

            currentHolder.activity.lifecycle.removeObserver(observer)
            holder = null
        }
    }

    private companion object {
        val NFC_ACTIONS = setOf(
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
        )
        val logger = Logger.withTag("CardSdkProvider")
    }

    data class Holder(
        val activity: FragmentActivity,
        val sdk: TangemSdk,
        val nfcManager: NfcManager,
        val authenticationManager: AuthenticationManager,
    )
}
