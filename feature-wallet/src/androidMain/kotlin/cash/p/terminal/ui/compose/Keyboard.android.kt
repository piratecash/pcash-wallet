package cash.p.terminal.ui.compose

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.provider.Settings
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

@Composable
actual fun observeKeyboardState(): State<Keyboard> {
    val keyboardState = remember { mutableStateOf(Keyboard.Closed) }
    val view = LocalView.current
    DisposableEffect(view) {
        val onGlobalListener = object : ViewTreeObserver.OnGlobalLayoutListener {
            var currentView: View? = null

            override fun onGlobalLayout() {
                val viewTmp = currentView?: return

                val rect = Rect()
                viewTmp.getWindowVisibleDisplayFrame(rect)
                val screenHeight = viewTmp.rootView.height
                val keypadHeight = screenHeight - rect.bottom
                keyboardState.value = if (keypadHeight > screenHeight * 0.15) {
                    Keyboard.Opened
                } else {
                    Keyboard.Closed
                }
            }
        }
        onGlobalListener.currentView = view
        view.viewTreeObserver.addOnGlobalLayoutListener(onGlobalListener)

        onDispose {
            onGlobalListener.currentView = null
            view.viewTreeObserver.removeOnGlobalLayoutListener(onGlobalListener)
        }
    }

    return keyboardState
}

@Composable
actual fun rememberThirdPartyKeyboardCheck(): () -> Boolean {
    val context = LocalContext.current
    return remember(context) { { isUsingCustomKeyboard(context) } }
}

@Composable
actual fun rememberInputMethodPicker(): () -> Unit {
    val context = LocalContext.current
    return remember(context) { { context.inputMethodManager.showInputMethodPicker() } }
}

private val Context.inputMethodManager: InputMethodManager
    get() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

private fun isUsingCustomKeyboard(context: Context): Boolean {

    val inputMethodManager = context.inputMethodManager
    val inputMethodProperties = inputMethodManager.enabledInputMethodList
    for (i in 0 until inputMethodProperties.size) {
        val imi = inputMethodProperties[i]
        if (imi.id == Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.DEFAULT_INPUT_METHOD
            )
        ) {
            if ((imi.serviceInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) {
                return true
            }
        }
    }

    return false
}
