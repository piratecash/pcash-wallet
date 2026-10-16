package cash.p.terminal.modules.intro

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import cash.p.terminal.core.App
import cash.p.terminal.core.BaseActivity
import cash.p.terminal.modules.main.MainModule

class IntroActivity : BaseActivity() {

    private val nightMode by lazy {
        val uiMode =
            App.instance.resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)
        uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            IntroScreen(
                nightMode = nightMode,
                onFinish = {
                    MainModule.start(this)
                    finish()
                }
            )
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, IntroActivity::class.java)
            context.startActivity(intent)
        }
    }

}
