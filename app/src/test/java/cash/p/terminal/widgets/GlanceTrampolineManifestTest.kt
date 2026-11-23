package cash.p.terminal.widgets

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GlanceTrampolineManifestTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun getActivityInfo_glanceTrampolineActivities_notDeclared() {
        listOf(
            "androidx.glance.appwidget.action.ActionTrampolineActivity",
            "androidx.glance.appwidget.action.InvisibleActionTrampolineActivity",
        ).forEach { name ->
            assertThrows(name, PackageManager.NameNotFoundException::class.java) {
                context.packageManager.getActivityInfo(ComponentName(context, name), 0)
            }
        }
    }

    // Proves the test reads the merged manifest: this receiver comes from the same Glance manifest.
    @Test
    fun getReceiverInfo_glanceCallbackReceiver_declared() {
        context.packageManager.getReceiverInfo(
            ComponentName(context, "androidx.glance.appwidget.action.ActionCallbackBroadcastReceiver"),
            0
        )
    }
}
