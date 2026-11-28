package cash.p.terminal.feature.logging.settings

import io.horizontalsystems.core.IPinComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow

internal class AndroidLoginJournalSecrets(
    private val pinComponent: IPinComponent
) : LoginJournalSecrets {

    override val changes: Flow<Unit> get() = pinComponent.pinSetFlowable.asFlow()

    override fun isJournalSecretSet() = pinComponent.isLogLoggingPinSet()

    override fun isDeleteContactsSecretSet() = pinComponent.isDeleteContactsPinSet()

    override fun disableJournalSecret() = pinComponent.disableLogLoggingPin()

    override fun disableDeleteContactsSecret() = pinComponent.disableDeleteContactsPin()
}
