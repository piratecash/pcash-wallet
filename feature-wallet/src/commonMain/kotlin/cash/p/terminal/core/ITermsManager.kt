package cash.p.terminal.core

import cash.p.terminal.modules.settings.terms.TermsModule
import kotlinx.coroutines.flow.Flow

interface ITermsManager {
    val termsAcceptedSignalFlow: Flow<Boolean>
    val terms: List<TermsModule.TermType>
    val allTermsAccepted: Boolean
    fun acceptTerms()

    /** Re-reads the stored flag after the storage under it was cleared. */
    fun reload()
}
