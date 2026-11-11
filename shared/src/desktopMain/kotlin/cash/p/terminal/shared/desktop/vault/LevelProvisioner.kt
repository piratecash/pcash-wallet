package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.core.storage.desktop.SafeLink
import cash.p.terminal.core.tryOrNull
import cash.p.terminal.modules.pin.LevelOutcome
import cash.p.terminal.vault.EnvelopePayload
import cash.p.terminal.vault.UnlockResult
import cash.p.terminal.vault.VaultSession
import co.touchlab.kermit.Logger

enum class HiddenPasswordResolution { NEW, REUSE_EMPTY, IN_USE, FAILED }

enum class DuressAvailability { UNAVAILABLE, NOT_SET, SET }

/** Creates levels from the places the session root can give away. The caller serializes the calls. */
internal class LevelProvisioner(
    private val environment: StorageEnvironment,
    private val opener: SafeOpener,
    private val chain: SafeChain,
    private var vaultSession: VaultSession,
) {
    private val logger = Logger.withTag("LevelProvisioner")

    /** Wipes the session it replaces. */
    fun replaceVaultSession(replacement: VaultSession) {
        vaultSession.close()
        vaultSession = replacement
    }

    fun closeVaultSession() = vaultSession.close()

    val duressAvailability: DuressAvailability
        get() {
            val meta = chain.root.meta
            return when {
                meta.isHiddenRoot -> DuressAvailability.UNAVAILABLE
                meta.child != null -> DuressAvailability.SET
                else -> DuressAvailability.NOT_SET
            }
        }

    fun setDuressPassword(password: CharArray): LevelOutcome {
        val meta = chain.root.meta
        check(!meta.isHiddenRoot) { "A hidden wallet has no duress" }
        check(meta.collapse == null) { "The level below is being removed" }
        if (environment.passwordVault.opensAny(password)) return LevelOutcome.PASSWORD_IN_USE
        val child = meta.child ?: return createLevel(password, isHiddenRoot = false)
        seal(child.envelopeIndex, password, child.slot, child.key)
        return LevelOutcome.SUCCESS
    }

    fun createHidden(password: CharArray): LevelOutcome {
        if (environment.passwordVault.opensAny(password)) return LevelOutcome.PASSWORD_IN_USE
        return createLevel(password, isHiddenRoot = true)
    }

    /** A safe cannot tell a hidden root from another root's empty duress: both count as reusable. */
    fun resolveHiddenPassword(password: CharArray): HiddenPasswordResolution =
        when (val result = environment.passwordVault.unlock(password)) {
            UnlockResult.WrongPassword -> HiddenPasswordResolution.NEW
            UnlockResult.Corrupted, UnlockResult.LegacyFormat -> HiddenPasswordResolution.FAILED
            is UnlockResult.Success -> result.session.use {
                if (isEmptyForeignChain(it)) HiddenPasswordResolution.REUSE_EMPTY else HiddenPasswordResolution.IN_USE
            }
        }

    private fun createLevel(password: CharArray, isHiddenRoot: Boolean): LevelOutcome {
        val place = findPlace(isHiddenRoot) ?: return LevelOutcome.LIMIT_REACHED
        val safe = provision(place, password, isHiddenRoot) ?: return LevelOutcome.FAILED
        if (isHiddenRoot) safe.close() else chain.append(safe)
        return LevelOutcome.SUCCESS
    }

    /** The first safe of the chain that still has both a slot and an envelope. */
    private fun findPlace(isHiddenRoot: Boolean): Place? = chain.safes
        .firstOrNull { safe -> safe.meta.let { it.slotPool.isNotEmpty() && it.envelopePool.isNotEmpty() } }
        ?.let { source ->
            val meta = source.meta
            val slot = environment.randomElement(meta.slotPool)
            val envelope = environment.randomElement(meta.envelopePool)
            Place(
                source = source,
                slot = slot,
                envelope = envelope,
                slots = share(meta.slotPool - slot, isHiddenRoot),
                envelopes = share(meta.envelopePool - envelope, isHiddenRoot),
            )
        }

    /** A duress takes all; a hidden root has no link to its creator, so it needs places of its own. */
    private fun share(pool: Set<Int>, isHiddenRoot: Boolean): Share {
        if (!isHiddenRoot) return Share(kept = emptySet(), given = pool)
        val shuffled = environment.shuffled(pool)
        return Share(kept = shuffled.drop(shuffled.size / 2).toSet(), given = shuffled.take(shuffled.size / 2).toSet())
    }

    /**
     * The places are taken before the envelope goes in: an envelope that opens a slot still in a pool could
     * hand that slot out again. A crash in between only loses the capacity.
     */
    private fun provision(place: Place, password: CharArray, isHiddenRoot: Boolean): OpenSafe? {
        val sourceBefore = place.source.meta
        val key = environment.randomKey()
        val safe = createSafe(place, key, isHiddenRoot)
        try {
            val childLink = SafeLink(place.slot, key.copyOf(), place.envelope)
            place.source.dao.putMeta(
                sourceBefore.copy(
                    slotPool = place.slots.kept,
                    envelopePool = place.envelopes.kept,
                    child = if (isHiddenRoot) sourceBefore.child else childLink,
                )
            )
            seal(place.envelope, password, place.slot, key)
            return safe
        } catch (e: Exception) {
            logger.w(e) { "Creating a level failed" }
            tryOrNull { place.source.dao.putMeta(sourceBefore) }
            safe.close()
            environment.slots.erase(place.slot)
            return null
        } finally {
            key.fill(0)
        }
    }

    private fun createSafe(place: Place, key: ByteArray, isHiddenRoot: Boolean): OpenSafe = try {
        opener.create(
            SafeLink(place.slot, key.copyOf(), place.envelope),
            slotPool = place.slots.given,
            envelopePool = place.envelopes.given,
            isHiddenRoot = isHiddenRoot,
        )
    } catch (e: Throwable) {
        key.fill(0)
        environment.slots.erase(place.slot)
        throw e
    }

    private fun seal(envelope: Int, password: CharArray, slot: Int, slotKey: ByteArray) {
        val shared = checkNotNull(vaultSession.payload()) { "The vault session is closed" }
        shared.use {
            EnvelopePayload(slot, slotKey.copyOf(), it.sharedKey.copyOf()).use { payload ->
                environment.passwordVault.sealEnvelope(envelope, password, payload)
            }
        }
    }

    /** Failing to read a chain must not let it be taken over, so that counts as in use. */
    private fun isEmptyForeignChain(session: VaultSession): Boolean {
        val payload = checkNotNull(session.payload()) { "The vault session is closed" }
        val start = payload.use { SafeLink(it.slot, it.slotKey.copyOf(), session.envelopeIndex) }
        return try {
            hasNoPointers(start)
        } catch (e: Exception) {
            logger.w(e) { "A password's chain could not be read" }
            false
        }
    }

    private fun hasNoPointers(start: SafeLink): Boolean {
        var link: SafeLink? = start
        while (link != null) {
            val next = link
            if (chain.safes.any { it.link.slot == next.slot }) {
                next.key.fill(0)
                return false
            }
            link = opener.open(next).use { safe ->
                if (safe.dao.pointerCount() > 0) return false
                safe.meta.child
            }
        }
        return true
    }

    private class Place(
        val source: OpenSafe,
        val slot: Int,
        val envelope: Int,
        val slots: Share,
        val envelopes: Share,
    )

    private class Share(val kept: Set<Int>, val given: Set<Int>)
}
