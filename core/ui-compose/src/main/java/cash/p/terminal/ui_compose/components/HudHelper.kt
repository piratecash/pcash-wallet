package cash.p.terminal.ui_compose.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import androidx.annotation.DrawableRes
import cash.p.terminal.ui_compose.R

object HudHelper {

    fun showInProcessMessage(
        contenView: View,
        resId: Int,
        duration: SnackbarDuration = SnackbarDuration.SHORT,
        gravity: SnackbarGravity = SnackbarGravity.BOTTOM,
        showProgressBar: Boolean = true
    ): CustomSnackbar? {
        return showHudNotification(
            contentView = contenView,
            text = contenView.context.getString(resId),
            variant = if (showProgressBar) AppSnackbarVariant.InProgress else AppSnackbarVariant.Message,
            duration = duration,
            gravity = gravity,
        )
    }

    fun showSuccessMessage(
        contenView: View,
        resId: Int,
        duration: SnackbarDuration = SnackbarDuration.SHORT,
        gravity: SnackbarGravity = SnackbarGravity.BOTTOM,
        @DrawableRes icon: Int? = null,
    ): CustomSnackbar? {
        return showHudNotification(
            contentView = contenView,
            text = contenView.context.getString(resId),
            variant = AppSnackbarVariant.Success,
            duration = duration,
            gravity = gravity,
            icon = icon,
        )
    }

    fun showSuccessMessage(
        contenView: View,
        text: String,
        duration: SnackbarDuration = SnackbarDuration.SHORT,
        gravity: SnackbarGravity = SnackbarGravity.BOTTOM
    ): CustomSnackbar? {
        return showHudNotification(
            contentView = contenView,
            text = text,
            variant = AppSnackbarVariant.Success,
            duration = duration,
            gravity = gravity,
        )
    }

    fun showMessage(
        contentView: View,
        text: String,
        duration: SnackbarDuration = SnackbarDuration.SHORT,
        gravity: SnackbarGravity = SnackbarGravity.BOTTOM,
        @DrawableRes icon: Int? = null,
    ): CustomSnackbar? {
        return showHudNotification(
            contentView = contentView,
            text = text,
            variant = AppSnackbarVariant.Message,
            duration = duration,
            gravity = gravity,
            icon = icon,
        )
    }

    fun showErrorMessage(
        contenView: View,
        textRes: Int,
        gravity: SnackbarGravity = SnackbarGravity.BOTTOM
    ) {
        showErrorMessage(contenView, contenView.context.getString(textRes), gravity)
    }

    fun showErrorMessage(
        contentView: View,
        text: String,
        gravity: SnackbarGravity = SnackbarGravity.BOTTOM
    ): CustomSnackbar? {
        return showHudNotification(
            contentView = contentView,
            text = text,
            variant = AppSnackbarVariant.Error,
            duration = SnackbarDuration.LONG,
            gravity = gravity,
        )
    }

    fun showErrorMessage(
        contenView: View,
        resId: Int,
        duration: SnackbarDuration = SnackbarDuration.SHORT,
        gravity: SnackbarGravity = SnackbarGravity.BOTTOM,
        @DrawableRes icon: Int? = null,
    ): CustomSnackbar? {
        return showHudNotification(
            contentView = contenView,
            text = contenView.context.getString(resId),
            variant = AppSnackbarVariant.Error,
            duration = duration,
            gravity = gravity,
            icon = icon,
        )
    }

    fun showWarningMessage(
        contentView: View,
        resId: Int,
        duration: SnackbarDuration = SnackbarDuration.SHORT,
        gravity: SnackbarGravity = SnackbarGravity.BOTTOM
    ): CustomSnackbar? {
        return showHudNotification(
            contentView = contentView,
            text = contentView.context.getString(resId),
            variant = AppSnackbarVariant.Warning,
            duration = duration,
            gravity = gravity,
            icon = R.drawable.ic_attention_24,
        )
    }

    fun showPremiumMessage(
        contentView: View,
        resId: Int,
        duration: SnackbarDuration = SnackbarDuration.SHORT,
        gravity: SnackbarGravity = SnackbarGravity.BOTTOM
    ): CustomSnackbar? {
        return showHudNotification(
            contentView = contentView,
            text = contentView.context.getString(resId),
            variant = AppSnackbarVariant.Premium,
            duration = duration,
            gravity = gravity,
        )
    }

    fun vibrate(context: Context, durationMs: Long = 20L) {
        val vibrationEffect = VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        getVibrator(context)?.vibrate(vibrationEffect)
    }

    // Two short pulses (Pixel "flip to Shhh" style): buzz, pause, buzz.
    fun vibrateDouble(context: Context, pulseMs: Long = 30L, gapMs: Long = 90L) {
        val timings = longArrayOf(0L, pulseMs, gapMs, pulseMs)
        val vibrationEffect = VibrationEffect.createWaveform(timings, -1)
        getVibrator(context)?.vibrate(vibrationEffect)
    }

    private fun getVibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun showHudNotification(
        contentView: View,
        text: String,
        variant: AppSnackbarVariant,
        duration: SnackbarDuration,
        gravity: SnackbarGravity,
        @DrawableRes icon: Int? = null,
    ): CustomSnackbar? {

        val snackbar = CustomSnackbar.make(
            contentView = contentView,
            text = text,
            variant = variant,
            duration = duration,
            gravity = gravity,
            iconRes = icon,
        )
        snackbar?.show()

        return snackbar
    }
}
