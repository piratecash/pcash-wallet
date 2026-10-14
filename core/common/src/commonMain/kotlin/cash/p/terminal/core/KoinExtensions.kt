package cash.p.terminal.core

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.ParametersDefinition

inline fun <reified T> getKoinInstance(): T {
    return object : KoinComponent {
        val value: T by inject()
    }.value
}

inline fun <reified T> getKoinInstance(noinline parameters: ParametersDefinition): T {
    return object : KoinComponent {
        val value: T by inject(parameters = parameters)
    }.value
}
