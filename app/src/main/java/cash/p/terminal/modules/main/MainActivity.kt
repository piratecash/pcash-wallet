package cash.p.terminal.modules.main

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.BaseActivity
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.core.navigateWithTermsAccepted
import cash.p.terminal.core.notifications.TransactionNotificationManager
import cash.p.terminal.modules.calculator.lockscreen.CalculatorLockScreen
import cash.p.terminal.modules.calculator.lockscreen.CalculatorLockScreenActions
import cash.p.terminal.modules.calculator.lockscreen.CalculatorLockScreenViewModel
import cash.p.terminal.modules.createaccount.CreateAccountPage
import cash.p.terminal.modules.intro.IntroActivity
import cash.p.terminal.modules.keystore.KeyStoreActivity
import cash.p.terminal.modules.pin.ui.PinUnlock
import cash.p.terminal.modules.settings.appearance.AppIconService
import cash.p.terminal.modules.softwareupdate.AppUpdateChecker
import cash.p.terminal.modules.tonconnect.TonConnectNewPage
import cash.p.terminal.modules.tonconnect.TonConnectSendRequestPage
import cash.p.terminal.modules.walletconnect.request.WCRequestPage
import cash.p.terminal.modules.walletconnect.session.WCSessionPage
import cash.p.terminal.navigation.HSNavigation
import cash.p.terminal.tangem.domain.sdk.CardSdkProvider
import cash.p.terminal.ui_compose.theme.ComposeAppTheme
import com.reown.walletkit.client.Wallet
import io.horizontalsystems.core.BackgroundManager
import io.horizontalsystems.core.IPinComponent
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.viewmodel.koinViewModel

internal enum class CalculatorPauseProtection {
    None,
    SecureSnapshot,
    ShowCalculator,
}

internal fun calculatorPauseProtection(
    calculatorMode: Boolean,
    pinSet: Boolean,
    externalActivityLaunching: Boolean,
): CalculatorPauseProtection = when {
    !calculatorMode || !pinSet -> CalculatorPauseProtection.None
    externalActivityLaunching -> CalculatorPauseProtection.SecureSnapshot
    else -> CalculatorPauseProtection.ShowCalculator
}

internal fun shouldLockOnCreate(
    hasSavedInstanceState: Boolean,
    pinSet: Boolean,
    currentTaskId: Int,
    previouslyResumedTaskId: Int?,
): Boolean = !hasSavedInstanceState && pinSet && previouslyResumedTaskId != currentTaskId

open class MainActivity : BaseActivity() {

    val viewModel: MainActivityViewModel by viewModel()
    val navigation: HSNavigation by lazy { HSNavigation(viewModel.navBackStack) }
    private val cardSdkProvider: CardSdkProvider by inject()
    private val appIconService: AppIconService by inject()
    private val localStorage: ILocalStorage by inject()
    private val pinComponent: IPinComponent by inject()
    private val backgroundManager: BackgroundManager by inject()
    private val appUpdateChecker: AppUpdateChecker by inject()
    private var pinLockComposeView: ComposeView? = null
    private val pinLockScreenState = mutableStateOf(false)
    private var showPinLockScreen by pinLockScreenState
    private var externalActivitySnapshotSecured = false

    override fun onResume() {
        super.onResume()
        // Refresh lock state synchronously: BackgroundManager → PinComponent emits
        // EnterForeground on a different coroutine and may not have run yet, so the
        // check below would otherwise race and briefly hide the calculator overlay.
        pinComponent.willEnterForeground()
        if (externalActivitySnapshotSecured) {
            externalActivitySnapshotSecured = false
            if (!pinComponent.isLockedFlow.value) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        if (showPinLockScreen && !pinComponent.isLockedFlow.value) {
            showPinLockScreen = false
            pinLockComposeView?.visibility = GONE
            applyLockWindowFlags(isLocked = false, calculatorMode = true)
        } else if (pinComponent.isLockedFlow.value) {
            // Locked on return: a dialog left open in the background lives in its own
            // Window and would surface above the calculator/PIN disguise. The collect in
            // observeLockState reacts asynchronously, so close synchronously here too.
            closeWindowsAboveLockScreen(navigation, cardSdkProvider)
        }
        validate()
        appUpdateChecker.checkIfNeeded()
    }

    override fun onPause() {
        super.onPause()
        protectCalculatorScreenOnPause()
    }

    override fun onStop() {
        super.onStop()
        appIconService.applyPendingLauncherAliasUpdate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.isDeepLinkOrNotificationTap()) {
            navigation.removeLastUntil(MainPage::class, inclusive = false)
        }
        viewModel.setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // If SQLCipher failed, BaseActivity redirected to error screen - don't continue
        if (App.sqlCipherLoadFailed) return

        if (
            shouldLockOnCreate(
                hasSavedInstanceState = savedInstanceState != null,
                pinSet = pinComponent.isPinSet,
                currentTaskId = taskId,
                previouslyResumedTaskId = backgroundManager.currentActivity?.taskId,
            )
        ) {
            pinComponent.lock()
        }

        cardSdkProvider.register(this)

        setContentView(R.layout.activity_main)

        findViewById<ComposeView>(R.id.navHostComposeView).setContent {
            Nav3Host(navigation, isLocked = pinLockScreenState)
        }

        viewModel.navigateToMainLiveData.observe(this) {
            if (it) {
                navigation.removeLastUntil(MainPage::class, inclusive = false)
                viewModel.onNavigatedToMain()
            }
        }

        viewModel.wcEvent.observe(this) { wcEvent ->
            if (wcEvent != null) {
                when (wcEvent) {
                    is Wallet.Model.SessionRequest -> {
                        navigation.slideFromBottom(WCRequestPage())
                    }

                    is Wallet.Model.SessionProposal -> {
                        navigation.slideFromBottom(WCSessionPage(null))
                    }

                    else -> {}
                }

                viewModel.onWcEventHandled()
            }
        }

        lifecycleScope.launch {
            viewModel.tcSendRequest.collect { tcEvent ->
                if (tcEvent != null) {
                    navigation.slideFromBottom(TonConnectSendRequestPage())
                }
            }
        }

        viewModel.tcDappRequest.observe(this) { request ->
            if (request != null) {
                navigation.slideFromBottomForResult<TonConnectNewPage.Result>(
                    TonConnectNewPage(request.dAppRequest)
                ) { result ->
                    if (request.closeAppOnResult) {
                        if (result.approved) {
                            //Need delay to get connected before closing activity
                            closeAfterDelay()
                        } else {
                            finish()
                        }
                    }
                }
                viewModel.onTcDappRequestHandled()
            }
        }

        // Handle deeplink or notification tap on cold start (only on fresh launch, not on recreation)
        if (savedInstanceState == null && intent.isDeepLinkOrNotificationTap()) {
            viewModel.setIntent(intent)
        }

        val composeView = findViewById<ComposeView>(R.id.pinLockComposeView)
        pinLockComposeView = composeView
        applyLockScreenState(
            isLocked = pinComponent.isLockedFlow.value,
            calculatorMode = localStorage.isCalculatorModeEnabled,
        )
        composeView.setContent {
            ComposeAppTheme {
                val calculatorMode by localStorage.isCalculatorModeEnabledFlow
                    .collectAsStateWithLifecycle()
                if (calculatorMode) {
                    val viewModel: CalculatorLockScreenViewModel = koinViewModel()
                    val state = viewModel.uiState
                    LaunchedEffect(state.unlocked) {
                        if (state.unlocked) {
                            showPinLockScreen = false
                            viewModel.onUnlockedConsumed()
                        }
                    }
                    if (showPinLockScreen) {
                        CalculatorLockScreen(
                            uiState = state,
                            actions = CalculatorLockScreenActions(
                                onDigit = viewModel::onDigitClick,
                                onOperator = viewModel::onOperatorClick,
                                onDecimal = viewModel::onDecimalClick,
                                onParen = viewModel::onParenClick,
                                onToggleSign = viewModel::onToggleSignClick,
                                onDelete = viewModel::onDeleteClick,
                                onClear = viewModel::onClearClick,
                                onEquals = viewModel::onEqualsClick,
                            ),
                        )
                    }
                } else {
                    PinUnlock(
                        showPinLockScreen = showPinLockScreen,
                        onSuccess = {
                            showPinLockScreen = false
                        }
                    )
                }
            }
        }
        observeLockState()
    }

    private fun closeAfterDelay() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ finish() }, 1000)
    }

    private fun validate() = try {
        viewModel.validate()
    } catch (e: MainScreenValidationError.NoSystemLock) {
        KeyStoreActivity.startForNoSystemLock(this)
        finish()
    } catch (e: MainScreenValidationError.KeyInvalidated) {
        KeyStoreActivity.startForInvalidKey(this)
        finish()
    } catch (e: MainScreenValidationError.UserAuthentication) {
        KeyStoreActivity.startForUserAuthentication(this)
        finish()
    } catch (e: MainScreenValidationError.Welcome) {
        IntroActivity.start(this)
        finish()
    } catch (e: MainScreenValidationError.KeystoreRuntimeException) {
        Toast.makeText(App.instance, "Issue with Keystore", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun observeLockState() {
        lifecycleScope.launch {
            combine(
                viewModel.isLockedFlow,
                localStorage.isCalculatorModeEnabledFlow,
            ) { locked, calculatorMode -> locked to calculatorMode }
                .collect { (isLocked, calculatorMode) ->
                    applyLockScreenState(isLocked, calculatorMode)
                }
        }
    }

    private fun applyLockScreenState(isLocked: Boolean, calculatorMode: Boolean) {
        showPinLockScreen = isLocked
        pinLockComposeView?.visibility = if (isLocked) VISIBLE else GONE
        applyTaskDescription(calculatorMode)
        applyLockWindowFlags(isLocked, calculatorMode)
        if (isLocked) {
            closeWindowsAboveLockScreen(navigation, cardSdkProvider)
        }
    }

    private fun protectCalculatorScreenOnPause() {
        when (
            calculatorPauseProtection(
                calculatorMode = localStorage.isCalculatorModeEnabled,
                pinSet = pinComponent.isPinSet,
                externalActivityLaunching = pinComponent.consumeExternalActivityLaunch(),
            )
        ) {
            CalculatorPauseProtection.None -> Unit
            CalculatorPauseProtection.SecureSnapshot -> {
                externalActivitySnapshotSecured = true
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            CalculatorPauseProtection.ShowCalculator -> {
                val composeView = pinLockComposeView ?: return
                showPinLockScreen = true
                composeView.visibility = VISIBLE
                applyTaskDescription(calculatorMode = true)
                applyLockWindowFlags(isLocked = true, calculatorMode = true)
            }
        }
    }

    private fun applyLockWindowFlags(isLocked: Boolean, calculatorMode: Boolean) {
        if (isLocked) {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
            window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            if (calculatorMode) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        } else {
            // FLAG_SECURE is left to Nav3Host: a page with screenshots disabled may be on screen.
            window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
    }

    @Suppress("DEPRECATION")
    private fun applyTaskDescription(calculatorMode: Boolean) {
        val labelRes = if (calculatorMode) R.string.calculator_app_name else R.string.App_Name
        setTitle(labelRes)
        setTaskDescription(ActivityManager.TaskDescription(getString(labelRes)))
    }

    fun openCreateNewWallet() {
        viewModel.selectBalanceTabOnNextLaunch()
        // Set flag to select Balance tab when returning to main screen
        // Open create wallet screen after PIN is created, clearing back stack to main screen
        navigation.navigateWithTermsAccepted {
            navigation.slideFromRightClearingBackStack(
                page = CreateAccountPage(
                    CreateAccountPage.Input(
                        popOffOnSuccess = MainPage::class,
                        popOffInclusive = false
                    )
                ),
                popUpTo = MainPage::class
            )
        }
    }
}

internal fun Intent.isDeepLinkOrNotificationTap(): Boolean =
    (data != null && action == Intent.ACTION_VIEW) ||
        hasExtra(TransactionNotificationManager.EXTRA_RECORD_UID)

// The Tangem NFC reader and bottom sheets live in their own windows, so they can render above
// the in-activity lock/calculator screen.
internal fun closeWindowsAboveLockScreen(navigation: HSNavigation, cardSdkProvider: CardSdkProvider) {
    cardSdkProvider.cancelSession()
    navigation.removeTrailingBottomSheets()
}
