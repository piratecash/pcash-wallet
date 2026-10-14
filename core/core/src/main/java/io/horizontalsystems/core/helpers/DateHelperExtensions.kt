package io.horizontalsystems.core.helpers

import android.content.Context
import cash.p.terminal.core.R

fun DateHelper.getTxDurationString(context: Context, durationInSec: Long): String {
    return when {
        durationInSec < 10 -> context.getString(R.string.Duration_instant)
        durationInSec < 60 -> context.getString(R.string.Duration_Seconds, durationInSec)
        durationInSec < 60 * 60 -> {
            val minutes = durationInSec / 60
            context.getString(R.string.Duration_Minutes, minutes)
        }

        else -> {
            val hours = durationInSec / (60 * 60)
            context.getString(R.string.Duration_Hours, hours)
        }
    }
}

fun DateHelper.getTxDurationIntervalString(context: Context, durationInSec: Long): String {
    return when {
        durationInSec < 10 -> context.getString(R.string.Duration_instant)
        durationInSec < 60 -> {
            val seconds = context.getString(R.string.Duration_Seconds, durationInSec)
            context.getString(R.string.Duration_Within, seconds)
        }

        durationInSec < 60 * 60 -> {
            val minutes = context.getString(R.string.Duration_Minutes, durationInSec / 60)
            context.getString(R.string.Duration_Within, minutes)
        }

        else -> {
            val hours = context.getString(R.string.Duration_Hours, durationInSec / (60 * 60))
            context.getString(R.string.Duration_Within, hours)
        }
    }
}
