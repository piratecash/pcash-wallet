package cash.p.terminal.ui_compose

import android.os.Bundle
import android.os.Parcelable
import androidx.core.os.BundleCompat

inline fun <reified T : Parcelable> Bundle.parcelable(key: String): T? =
    BundleCompat.getParcelable(this, key, T::class.java)
