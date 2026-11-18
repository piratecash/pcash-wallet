package cash.p.terminal.core.managers

import cash.p.terminal.core.tryOrNull
import io.horizontalsystems.tonkit.Address

/** One spelling for every form of a TON account (raw, bounceable, non-bounceable); null when it does not parse. */
internal fun tonRawAddressOrNull(address: String): String? = tryOrNull { Address.parse(address).toRaw() }
