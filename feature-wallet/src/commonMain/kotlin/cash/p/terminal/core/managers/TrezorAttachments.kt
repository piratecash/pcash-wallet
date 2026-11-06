package cash.p.terminal.core.managers

import kotlinx.coroutines.flow.Flow

interface TrezorAttachments {
    /** Emits each time a Trezor is plugged in, while collected. */
    val attached: Flow<Unit>

    /** Emits each time a Trezor is unplugged, while collected. */
    val detached: Flow<Unit>
}
