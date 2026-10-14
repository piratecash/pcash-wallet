package io.horizontalsystems.core

import android.app.Application

// core:common cannot reach the app Context by itself; CoreApp.instance fills this.
object ApplicationHolder {
    lateinit var application: Application
}
