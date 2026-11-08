package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.modules.pin.LevelOutcome
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.DURESS_ACCOUNT
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.MAIN_ACCOUNT
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.NESTED_ACCOUNT
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val STEPS_BEFORE_ENVELOPES_FREED = listOf(
    StorageCheckpoint.COLLAPSE_INTENT_WRITTEN,
    StorageCheckpoint.COLLAPSE_ACCOUNTS_ADOPTED,
)

private val EVERY_STEP = STEPS_BEFORE_ENVELOPES_FREED + listOf(
    StorageCheckpoint.COLLAPSE_ENVELOPES_FREED,
    StorageCheckpoint.COLLAPSE_ENVELOPES_MARKED,
    StorageCheckpoint.COLLAPSE_SLOT_ERASED,
    StorageCheckpoint.COLLAPSE_POOL_RETURNED,
)

class CollapseRecoveryTest {

    @Test
    fun collapseLevelsAbove_disableDuress_returnsAccountsAndPool() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        fixture.writeKitRows(DURESS_ACCOUNT, "before")
        fixture.reloginOk(DURESS_PASSWORD)
        val duress = fixture.session.chain.root
        val duressSlot = duress.link.slot
        val duressEnvelope = duress.link.envelopeIndex
        val duressMeta = duress.meta
        fixture.reloginOk()

        fixture.router.collapseLevelsAbove(0)

        assertEquals(mapOf(MAIN_ACCOUNT to 0, DURESS_ACCOUNT to 0), fixture.visibleAccounts())
        val meta = fixture.session.chain.root.meta
        assertNull(meta.child)
        assertNull(meta.collapse)
        assertEquals(duressMeta.slotPool + duressSlot, meta.slotPool)
        assertEquals(duressMeta.envelopePool + duressEnvelope, meta.envelopePool)
        assertEquals(1, fixture.store(DURESS_ACCOUNT).walletsDao().enabledCoins(DURESS_ACCOUNT).size)
        assertIs<UnlockOutcome.WrongPassword>(fixture.relogin(DURESS_PASSWORD))
    }

    @Test
    fun collapseLevelsAbove_crashAfterEachStep_nextMainLoginFinishesWithDataIntact() {
        EVERY_STEP.forEach { step ->
            levelsScenario { fixture ->
                fixture.mainWithDuress()
                fixture.writeKitRows(DURESS_ACCOUNT, "before")
                fixture.checkpoints.crashAt(step)

                fixture.assertCrashes { fixture.router.collapseLevelsAbove(0) }

                fixture.reloginOk()
                assertEquals(mapOf(MAIN_ACCOUNT to 0, DURESS_ACCOUNT to 0), fixture.visibleAccounts(), "$step")
                assertEquals(1, fixture.store(DURESS_ACCOUNT).walletsDao().enabledCoins(DURESS_ACCOUNT).size, "$step")
                assertNotNull(fixture.store(DURESS_ACCOUNT).moneroFileDao().getAssociatedRecord(DURESS_ACCOUNT), "$step")
                val meta = fixture.session.chain.root.meta
                assertNull(meta.collapse, "$step")
                assertNull(meta.child, "$step")
                assertEquals(1, fixture.session.chain.safes.size, "$step")
                assertIs<UnlockOutcome.WrongPassword>(fixture.relogin(DURESS_PASSWORD), "$step")
            }
        }
    }

    @Test
    fun collapseLevelsAbove_crashDuringRecovery_theNextLoginStillFinishes() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        fixture.checkpoints.crashAt(StorageCheckpoint.COLLAPSE_ACCOUNTS_ADOPTED)
        fixture.assertCrashes { fixture.router.collapseLevelsAbove(0) }

        fixture.checkpoints.crashAt(StorageCheckpoint.COLLAPSE_SLOT_ERASED)
        assertTrue(runCatchingCrash { fixture.relogin() })

        fixture.reloginOk()
        assertEquals(mapOf(MAIN_ACCOUNT to 0, DURESS_ACCOUNT to 0), fixture.visibleAccounts())
        assertNull(fixture.session.chain.root.meta.collapse)
    }

    @Test
    fun recover_crashBeforeEnvelopesFreedThenWalletCreatedInDuress_walletIsThereInMain() {
        STEPS_BEFORE_ENVELOPES_FREED.forEach { step ->
            levelsScenario { fixture ->
                fixture.mainWithDuress()
                fixture.checkpoints.crashAt(step)
                fixture.assertCrashes { fixture.router.collapseLevelsAbove(0) }

                fixture.reloginOk(DURESS_PASSWORD)
                fixture.addAccount("created-in-duress")
                fixture.writeKitRows("created-in-duress", "duress")

                fixture.reloginOk()
                assertEquals(
                    mapOf(MAIN_ACCOUNT to 0, DURESS_ACCOUNT to 0, "created-in-duress" to 0),
                    fixture.visibleAccounts(),
                    "$step",
                )
                assertEquals(1, fixture.store("created-in-duress").walletsDao().enabledCoins("created-in-duress").size)
            }
        }
    }

    @Test
    fun recover_crashBeforeEnvelopesFreedThenHiddenCreatedInDuress_keepsItsPlacesOutOfThePoolAndItOpens() {
        STEPS_BEFORE_ENVELOPES_FREED.forEach { step ->
            levelsScenario { fixture ->
                fixture.mainWithDuress()
                fixture.checkpoints.crashAt(step)
                fixture.assertCrashes { fixture.router.collapseLevelsAbove(0) }

                fixture.reloginOk(DURESS_PASSWORD)
                fixture.createHidden()
                fixture.reloginOk(HIDDEN_PASSWORD)
                fixture.addAccount("hidden-account")
                val hidden = fixture.session.chain.root.link.let { it.slot to it.envelopeIndex }
                val hiddenPool = fixture.session.chain.root.meta

                fixture.reloginOk()
                val meta = fixture.session.chain.root.meta
                assertNull(meta.collapse, "$step")
                assertFalse(hidden.first in meta.slotPool, "$step")
                assertFalse(hidden.second in meta.envelopePool, "$step")
                assertTrue(meta.slotPool.intersect(hiddenPool.slotPool).isEmpty(), "$step")
                assertTrue(meta.envelopePool.intersect(hiddenPool.envelopePool).isEmpty(), "$step")

                fixture.reloginOk(HIDDEN_PASSWORD)
                assertEquals(mapOf("hidden-account" to 0), fixture.visibleAccounts(), "$step")
            }
        }
    }

    @Test
    fun recover_lowerSafeUnreadableBeforeEnvelopesFreed_waitsAndErasesNothing() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        fixture.reloginOk(DURESS_PASSWORD)
        val duressSlot = fixture.session.chain.root.link.slot
        fixture.reloginOk()
        fixture.checkpoints.crashAt(StorageCheckpoint.COLLAPSE_INTENT_WRITTEN)
        fixture.assertCrashes { fixture.router.collapseLevelsAbove(0) }
        fixture.vault.close()
        val safeFile = fixture.files.safeFile(duressSlot)
        val saved = safeFile.readBytes()
        safeFile.writeBytes(ByteArray(saved.size) { 7 })

        fixture.reloginOk()
        assertEquals(mapOf(MAIN_ACCOUNT to 0), fixture.visibleAccounts())
        assertNotNull(fixture.session.chain.root.meta.collapse)
        assertFalse(duressSlot in fixture.session.chain.root.meta.slotPool)
        assertTrue(safeFile.readBytes().all { it == 7.toByte() })

        fixture.vault.close()
        safeFile.writeBytes(saved)
        fixture.reloginOk()
        assertEquals(mapOf(MAIN_ACCOUNT to 0, DURESS_ACCOUNT to 0), fixture.visibleAccounts())
        assertNull(fixture.session.chain.root.meta.collapse)
    }

    @Test
    fun unlock_mainWithDuressConfigured_doesNotRunTheCollapse() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        fixture.checkpoints.reached.clear()

        fixture.reloginOk()

        assertTrue(fixture.checkpoints.reached.isEmpty())
        assertEquals(mapOf(MAIN_ACCOUNT to 0, DURESS_ACCOUNT to 1), fixture.visibleAccounts())
        fixture.reloginOk(DURESS_PASSWORD)
        assertEquals(mapOf(DURESS_ACCOUNT to 0), fixture.visibleAccounts())
    }

    @Test
    fun collapseLevelsAbove_kitWritingAtEveryStep_keepsAllItsRows() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        val writes = mutableListOf<String>()
        fixture.checkpoints.onReached = { step ->
            runBlocking { fixture.writeKitRows(DURESS_ACCOUNT, step.name) }
            writes += "evm-token-${step.name}"
        }

        fixture.router.collapseLevelsAbove(0)
        fixture.checkpoints.onReached = {}

        fixture.reloginOk()
        val wallets = fixture.store(DURESS_ACCOUNT).walletsDao().enabledCoins(DURESS_ACCOUNT)
        assertEquals(writes.toSet(), wallets.map { it.tokenQueryId }.toSet())
        assertTrue(writes.size >= EVERY_STEP.size)
        val record = assertNotNull(fixture.store(DURESS_ACCOUNT).moneroFileDao().getAssociatedRecord(DURESS_ACCOUNT))
        assertEquals("monero-${StorageCheckpoint.COLLAPSE_POOL_RETURNED.name}", record.fileName.value)
    }

    @Test
    fun collapseLevelsAbove_duringTheErase_theSlotIsNotInThePoolUntilItIsNoise() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        fixture.reloginOk(DURESS_PASSWORD)
        val duressSlot = fixture.session.chain.root.link.slot
        val duressKey = fixture.session.chain.root.link.key.copyOf()
        fixture.reloginOk()
        val root = fixture.session.chain.root
        var erasedBeforeReturn = false
        fixture.checkpoints.onReached = { step ->
            if (step != StorageCheckpoint.COLLAPSE_POOL_RETURNED) {
                assertFalse(duressSlot in root.meta.slotPool, "$step")
            }
            if (step == StorageCheckpoint.COLLAPSE_SLOT_ERASED) {
                erasedBeforeReturn = !fixture.opensWith(fixture.files.safeFile(duressSlot), duressKey)
            }
        }

        fixture.router.collapseLevelsAbove(0)
        fixture.checkpoints.onReached = {}

        assertTrue(erasedBeforeReturn)
        assertTrue(duressSlot in root.meta.slotPool)
    }

    @Test
    fun setDuressPassword_collapseStillWaiting_failsAndHandsOutNothing() = levelsScenario { fixture ->
        fixture.mainWithDuress()
        fixture.reloginOk(DURESS_PASSWORD)
        val slot = fixture.session.chain.root.link.slot
        fixture.reloginOk()
        fixture.checkpoints.crashAt(StorageCheckpoint.COLLAPSE_INTENT_WRITTEN)
        fixture.assertCrashes { fixture.router.collapseLevelsAbove(0) }
        fixture.vault.close()
        val safeFile = fixture.files.safeFile(slot)
        val saved = safeFile.readBytes()
        safeFile.writeBytes(ByteArray(saved.size) { 7 })
        fixture.reloginOk()
        val poolBefore = fixture.session.chain.root.meta.slotPool

        assertEquals(LevelOutcome.FAILED, fixture.vault.setDuressPassword(password(NESTED_PASSWORD)))

        assertEquals(poolBefore, fixture.session.chain.root.meta.slotPool)
        fixture.vault.close()
        safeFile.writeBytes(saved)
    }

    @Test
    fun recover_crashAfterTheEraseInNestedChain_returnsThePoolAndEveryNestedSlot() = levelsScenario { fixture ->
        fixture.mainWithNestedDuress()
        val places = nestedPlaces(fixture)
        fixture.reloginOk()
        val poolBefore = fixture.session.chain.root.meta.slotPool
        fixture.checkpoints.crashAt(StorageCheckpoint.COLLAPSE_SLOT_ERASED, occurrence = 2)
        fixture.assertCrashes { fixture.router.collapseLevelsAbove(0) }

        fixture.reloginOk()

        val meta = fixture.session.chain.root.meta
        assertNull(meta.child)
        assertNull(meta.collapse)
        assertEquals(poolBefore + places.slots + places.slotPool, meta.slotPool)
        assertEquals(places.envelopes + places.envelopePool, meta.envelopePool)
        assertEquals(
            mapOf(MAIN_ACCOUNT to 0, DURESS_ACCOUNT to 0, NESTED_ACCOUNT to 0),
            fixture.visibleAccounts(),
        )
        places.keys.forEach { (slot, key) -> assertFalse(fixture.opensWith(fixture.files.safeFile(slot), key), "$slot") }
        assertIs<UnlockOutcome.WrongPassword>(fixture.relogin(NESTED_PASSWORD))
    }

    @Test
    fun collapseLevelsAbove_hiddenCreatedFromTheNestedChain_returnsOnlyWhatTheChainStillOwns() = levelsScenario { fixture ->
        fixture.mainWithNestedDuress()
        fixture.reloginOk(DURESS_PASSWORD)
        fixture.createHidden()
        val places = nestedPlaces(fixture)
        fixture.reloginOk(HIDDEN_PASSWORD)
        fixture.addAccount("hidden-account")
        val hiddenSafe = fixture.session.chain.root
        val hiddenSlots = hiddenSafe.meta.slotPool + hiddenSafe.link.slot
        val hiddenEnvelopes = hiddenSafe.meta.envelopePool + hiddenSafe.link.envelopeIndex
        fixture.reloginOk()

        fixture.router.collapseLevelsAbove(0)

        val meta = fixture.session.chain.root.meta
        assertEquals(places.slots + places.slotPool, meta.slotPool)
        assertEquals(places.envelopes + places.envelopePool, meta.envelopePool)
        assertTrue(meta.slotPool.intersect(hiddenSlots).isEmpty())
        assertTrue(meta.envelopePool.intersect(hiddenEnvelopes).isEmpty())
        fixture.reloginOk(HIDDEN_PASSWORD)
        assertEquals(mapOf("hidden-account" to 0), fixture.visibleAccounts())
        assertEquals(LevelOutcome.SUCCESS, fixture.vault.createHiddenWallet(password(NESTED_PASSWORD)))
    }

    @Test
    fun collapseLevelsAbove_ofALevelInTheChain_keepsTheLevelsAbove() = levelsScenario { fixture ->
        fixture.mainWithNestedDuress()

        fixture.router.collapseLevelsAbove(1)

        assertEquals(
            mapOf(MAIN_ACCOUNT to 0, DURESS_ACCOUNT to 1, NESTED_ACCOUNT to 1),
            fixture.visibleAccounts(),
        )
        fixture.reloginOk(DURESS_PASSWORD)
        assertEquals(mapOf(DURESS_ACCOUNT to 0, NESTED_ACCOUNT to 0), fixture.visibleAccounts())
        assertIs<UnlockOutcome.WrongPassword>(fixture.relogin(NESTED_PASSWORD))
    }

    private suspend fun nestedPlaces(fixture: VaultLevelsFixture): NestedPlaces {
        fixture.reloginOk(DURESS_PASSWORD)
        val safes = fixture.session.chain.safes
        val places = NestedPlaces(
            keys = safes.associate { it.link.slot to it.link.key.copyOf() },
            slots = safes.map { it.link.slot }.toSet(),
            envelopes = safes.map { it.link.envelopeIndex }.toSet(),
            slotPool = safes.flatMap { it.meta.slotPool }.toSet(),
            envelopePool = safes.flatMap { it.meta.envelopePool }.toSet(),
        )
        assertEquals(2, safes.size)
        return places
    }

    private suspend fun runCatchingCrash(block: suspend () -> Unit): Boolean = try {
        block()
        false
    } catch (e: SimulatedCrash) {
        true
    }

    private class NestedPlaces(
        val keys: Map<Int, ByteArray>,
        val slots: Set<Int>,
        val envelopes: Set<Int>,
        val slotPool: Set<Int>,
        val envelopePool: Set<Int>,
    )
}
