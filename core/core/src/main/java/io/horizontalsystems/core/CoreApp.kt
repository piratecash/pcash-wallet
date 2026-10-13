package io.horizontalsystems.core

import android.app.Application
import android.content.Context
import cash.p.terminal.strings.helpers.LocaleHelper
import org.koin.java.KoinJavaComponent.inject
import java.util.Locale

abstract class CoreApp : Application() {

    companion object : ICoreApp {
        override val backgroundManager: BackgroundManager by inject(BackgroundManager::class.java)
        override val encryptionManager: IEncryptionManager by inject(IEncryptionManager::class.java)
        override val pinComponent: IPinComponent by inject(IPinComponent::class.java)

        override lateinit var instance: CoreApp
    }

    abstract fun localizedContext(): Context
    abstract val isSwapEnabled: Boolean

    fun localeAwareContext(base: Context): Context {
        return LocaleHelper.onAttach(base)
    }

    fun getLocale(): Locale {
        return LocaleHelper.getLocale(this)
    }

    fun setLocale(currentLocale: Locale) {
        LocaleHelper.setLocale(this, currentLocale)
    }

    fun isLocaleRTL(): Boolean {
        return LocaleHelper.isRTL(Locale.getDefault())
    }
}
