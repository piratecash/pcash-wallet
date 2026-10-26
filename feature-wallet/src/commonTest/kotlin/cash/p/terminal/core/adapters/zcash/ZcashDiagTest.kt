package cash.p.terminal.core.adapters.zcash

import cash.p.terminal.wallet.AdapterState
import cash.p.terminal.wallet.entities.TokenType.AddressSpecType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ZcashDiagTest {

    // ---- magnitudeBucket ----

    @Test
    fun magnitudeBucket_boundaries_fallIntoUpperInterval() {
        assertEquals("0", magnitudeBucket(BigDecimal.ZERO))
        assertEquals("<0.1", magnitudeBucket(BigDecimal("0.05")))
        assertEquals("0.1-1", magnitudeBucket(BigDecimal("0.1")))
        assertEquals("0.1-1", magnitudeBucket(BigDecimal("0.5")))
        assertEquals("1-10", magnitudeBucket(BigDecimal.ONE))
        assertEquals("1-10", magnitudeBucket(BigDecimal("4.065")))
        assertEquals("10-100", magnitudeBucket(BigDecimal.TEN))
        assertEquals(">=100", magnitudeBucket(BigDecimal("100")))
        assertEquals(">=100", magnitudeBucket(BigDecimal("1000")))
    }

    @Test
    fun magnitudeBucket_scaleVariants_compareByValue() {
        assertEquals("0.1-1", magnitudeBucket(BigDecimal("0.10")))
        assertEquals("1-10", magnitudeBucket(BigDecimal("1.00")))
    }

    // ---- diagFields ----

    @Test
    fun diagFields_nullHeights_renderDash() {
        val fields = diagFields(
            snapshot(
                chainTipHeight = null,
                scannedHeight = null,
                syncTargetHeight = null,
            )
        )
        assertEquals("—", fields["chainTipHeight"])
        assertEquals("—", fields["scannedHeight"])
        assertEquals("—", fields["syncTargetHeight"])
        assertEquals("—", fields["scanGap"])
    }

    @Test
    fun diagFields_bothHeightsKnown_rendersScanGap() {
        assertEquals(
            "100",
            diagFields(snapshot(chainTipHeight = 2_400_000, scannedHeight = 2_399_900))["scanGap"]
        )
    }

    @Test
    fun diagFields_valuePendingBucket_reflectsValuePendingNotChangePending() {
        val fields = diagFields(
            snapshot(
                changePending = BigDecimal("250"),
                valuePending = BigDecimal("4.065"),
            )
        )
        assertEquals("1-10", fields["valuePendingBucket"])
        assertEquals(">=100", fields["changePendingBucket"])
        assertEquals("true", fields["valuePending>0"])
        assertEquals("true", fields["changePending>0"])
    }

    // ---- poolLabel ----

    @Test
    fun poolLabel_mapsEachSpecAndNull() {
        assertEquals("Shielded", poolLabel(AddressSpecType.Shielded))
        assertEquals("Transparent", poolLabel(AddressSpecType.Transparent))
        assertEquals("Unified", poolLabel(AddressSpecType.Unified))
        assertEquals("Sapling", poolLabel(null))
    }

    // ---- privacy ----

    @Test
    fun diagFields_neverContainsExactAmount() {
        val exactValuePending = BigDecimal("4.06512345")
        val exactChangePending = BigDecimal("123.98765432")
        val exactAvailable = BigDecimal("7.55555555")
        val rendered = diagFields(
            snapshot(
                available = exactAvailable,
                changePending = exactChangePending,
                valuePending = exactValuePending,
            )
        ).toString()

        assertFalse(rendered.contains("4.06512345"))
        assertFalse(rendered.contains("123.98765432"))
        assertFalse(rendered.contains("7.55555555"))
        // Also not the raw Zatoshi integer forms.
        assertFalse(rendered.contains("406512345"))
        assertFalse(rendered.contains("12398765432"))
    }

    // ---- unknown balance (not-yet-loaded) is distinct from zero ----

    @Test
    fun diagFields_unknownBalance_rendersUnknownNotFalseOrZero() {
        val fields = diagFields(snapshot(available = null, changePending = null, valuePending = null))
        assertEquals("unknown", fields["available>0"])
        assertEquals("unknown", fields["changePending>0"])
        assertEquals("unknown", fields["valuePending>0"])
        assertEquals("unknown", fields["valuePendingBucket"])
        assertEquals("unknown", fields["changePendingBucket"])
    }

    @Test
    fun diagFields_zeroBalance_rendersFalseAndZeroBucket() {
        val fields = diagFields(
            snapshot(available = BigDecimal.ZERO, changePending = BigDecimal.ZERO, valuePending = BigDecimal.ZERO)
        )
        assertEquals("false", fields["available>0"])
        assertEquals("false", fields["valuePending>0"])
        assertEquals("0", fields["valuePendingBucket"])
        assertEquals("0", fields["changePendingBucket"])
    }

    // ---- safeSyncStateLabel ----

    @Test
    fun safeSyncStateLabel_notSyncedWithMessage_showsMessage() {
        val state = AdapterState.NotSynced(RuntimeException("Key import failed"))
        assertEquals("NotSynced Key import failed", safeSyncStateLabel(state))
    }

    @Test
    fun safeSyncStateLabel_multilineMessage_showsFirstLineOnly() {
        val state = AdapterState.NotSynced(RuntimeException("first line\nsecond line"))
        assertEquals("NotSynced first line", safeSyncStateLabel(state))
    }

    @Test
    fun safeSyncStateLabel_longMessage_truncatedToLimit() {
        val state = AdapterState.NotSynced(RuntimeException("x".repeat(MAX_ERROR_LABEL + 50)))
        assertEquals("NotSynced " + "x".repeat(MAX_ERROR_LABEL), safeSyncStateLabel(state))
    }

    @Test
    fun safeSyncStateLabel_nullOrBlankMessage_showsClassName() {
        assertEquals(
            "NotSynced IllegalStateException",
            safeSyncStateLabel(AdapterState.NotSynced(IllegalStateException()))
        )
        assertEquals(
            "NotSynced IllegalStateException",
            safeSyncStateLabel(AdapterState.NotSynced(IllegalStateException("  ")))
        )
    }

    @Test
    fun safeSyncStateLabel_messageWithCredentialUrl_redactsSecrets() {
        val state = AdapterState.NotSynced(
            RuntimeException("connection failed: https://user:secret@upstream.example/v3/abcdef?token=sekrit")
        )
        val label = safeSyncStateLabel(state)
        assertTrue(label.contains("NotSynced"))
        assertTrue(label.contains("upstream.example"))
        listOf("user:secret", "abcdef", "sekrit").forEach { assertFalse(label, label.contains(it)) }
    }

    @Test
    fun safeSyncStateLabel_messageWithViewingKey_redactsTheKey() {
        val key = "uview1" + "q".repeat(70)
        val label = safeSyncStateLabel(AdapterState.NotSynced(RuntimeException("Invalid key: $key")))
        assertTrue(label.contains("NotSynced"))
        assertTrue(label.contains("Invalid key"))
        assertFalse(label, label.contains(key))
        assertFalse(label, label.contains("qqqq"))
    }

    @Test
    fun safeSyncStateLabel_messageWithSpendingKey_redactsTheKey() {
        val key = "secret-extended-key-main1" + "q".repeat(70)
        val label = safeSyncStateLabel(AdapterState.NotSynced(RuntimeException("Bad $key end")))
        assertTrue(label.contains("Bad"))
        assertFalse(label, label.contains("qqqq"))
        assertFalse(label, label.contains("secret-extended-key"))
    }

    // ---- birthdayLabel ----

    @Test
    fun birthdayLabel_walletNotOpenedNothingConfigured_notSet() {
        assertEquals("not set (wallet not opened)", birthdayLabel(configured = null, wallet = null))
    }

    @Test
    fun birthdayLabel_walletNotOpenedConfigured_showsConfigured() {
        assertEquals("100 (configured, wallet not opened)", birthdayLabel(configured = 100L, wallet = null))
    }

    @Test
    fun birthdayLabel_walletOpenedMatchingOrUnconfigured_showsWalletOnly() {
        assertEquals("100", birthdayLabel(configured = 100L, wallet = 100))
        assertEquals("100", birthdayLabel(configured = null, wallet = 100))
    }

    @Test
    fun birthdayLabel_walletDiffersFromConfigured_showsBoth() {
        assertEquals("100 (configured 90)", birthdayLabel(configured = 90L, wallet = 100))
    }

    @Test
    fun safeSyncStateLabel_nonFailedStates_renderPlainToString() {
        assertEquals("Synced", safeSyncStateLabel(AdapterState.Synced))
        assertEquals("Connecting", safeSyncStateLabel(AdapterState.Connecting))
    }

    @Suppress("LongParameterList")
    private fun snapshot(
        pool: String = "Unified",
        syncStateDiscriminator: String = "Syncing",
        chainTipHeight: Long? = 2_400_000,
        scannedHeight: Long? = 2_399_900,
        syncTargetHeight: Long? = 2_400_000,
        available: BigDecimal? = BigDecimal("1.0"),
        changePending: BigDecimal? = BigDecimal.ZERO,
        valuePending: BigDecimal? = BigDecimal("4.065"),
    ): ZcashDiagSnapshot = ZcashDiagSnapshot(
        pool = pool,
        syncStateDiscriminator = syncStateDiscriminator,
        chainTipHeight = chainTipHeight,
        scannedHeight = scannedHeight,
        syncTargetHeight = syncTargetHeight,
        available = available,
        changePending = changePending,
        valuePending = valuePending,
    )
}
