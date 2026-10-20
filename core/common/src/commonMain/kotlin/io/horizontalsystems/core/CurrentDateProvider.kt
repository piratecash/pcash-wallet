package io.horizontalsystems.core

import java.util.Date

interface ICurrentDateProvider {
    val currentDate: Date
}

class CurrentDateProvider : ICurrentDateProvider {

    override val currentDate: Date
        get() = Date()

}
