package cash.p.terminal.core.managers

import cash.p.terminal.core.WalletOnboardingStorage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TermsManagerTest {

    private val storage = object : WalletOnboardingStorage {
        override var termsAccepted = false
        override var mainShowedOnce = false
        override var passphraseTermsAgreed = false
        override var safetyRulesAgreed = false
    }

    @Test
    fun reload_storageClearedAfterAcceptance_signalsTermsNotAccepted() {
        val manager = TermsManager(storage)
        manager.acceptTerms()
        assertTrue(manager.termsAcceptedSignalFlow.value)

        storage.termsAccepted = false
        manager.reload()

        assertFalse(manager.termsAcceptedSignalFlow.value)
    }
}
