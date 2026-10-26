package cash.p.terminal.core

import android.os.Process
import android.os.SystemClock

actual fun currentProcessId(): Long = Process.myPid().toLong()

actual fun currentProcessAgeMillis(): Long = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
