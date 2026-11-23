package cash.p.terminal.ledger.account

import com.solana.core.PublicKey
import com.solana.programs.AssociatedTokenProgram
import com.solana.programs.TokenProgram

/** The SPL TransferChecked of a message; [newAccountOwner] is set when the same message creates [destination]. */
class SolanaTokenTransfer(val mint: String, val destination: String, val newAccountOwner: String?) {

    companion object {
        /** The message's single TransferChecked, or null when it has none or several; throws on a malformed message. */
        fun parse(message: ByteArray): SolanaTokenTransfer? {
            val parsed = SolanaMessage.parse(message)
            val transfer = parsed.instructions
                .filter { parsed.key(it.programIndex) in TOKEN_PROGRAMS && it.data.firstOrNull() == TRANSFER_CHECKED }
                .singleOrNull() ?: return null
            val mint = parsed.account(transfer, MINT_INDEX) ?: return null
            val destination = parsed.account(transfer, DESTINATION_INDEX) ?: return null
            val newAccountOwner = parsed.instructions
                .firstOrNull {
                    parsed.key(it.programIndex) == ASSOCIATED_TOKEN_PROGRAM &&
                        parsed.account(it, CREATED_ACCOUNT_INDEX) == destination
                }
                ?.let { parsed.account(it, CREATED_OWNER_INDEX) }
            return SolanaTokenTransfer(mint, destination, newAccountOwner)
        }

        private const val TRANSFER_CHECKED: Byte = 12
        private const val MINT_INDEX = 1
        private const val DESTINATION_INDEX = 2
        private const val CREATED_ACCOUNT_INDEX = 1
        private const val CREATED_OWNER_INDEX = 2
        private val TOKEN_PROGRAMS = setOf(
            TokenProgram.PROGRAM_ID.toBase58(),
            "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb",
        )
        private val ASSOCIATED_TOKEN_PROGRAM = AssociatedTokenProgram.SPL_ASSOCIATED_TOKEN_ACCOUNT_PROGRAM_ID.toBase58()
    }
}

private class SolanaInstruction(val programIndex: Int, val accounts: List<Int>, val data: ByteArray)

/** The static account keys and instructions of a legacy or v0 message; lookup-table accounts resolve to null. */
private class SolanaMessage(private val keys: List<String>, val instructions: List<SolanaInstruction>) {

    fun key(index: Int): String? = keys.getOrNull(index)

    fun account(instruction: SolanaInstruction, position: Int): String? =
        instruction.accounts.getOrNull(position)?.let(::key)

    companion object {
        fun parse(message: ByteArray): SolanaMessage {
            val reader = ByteReader(message)
            if (message.first().toInt() and VERSIONED_PREFIX != 0) reader.skip(1)
            reader.skip(HEADER_SIZE)
            val keys = List(reader.compactU16()) { PublicKey(reader.bytes(KEY_SIZE)).toBase58() }
            reader.skip(KEY_SIZE)
            val instructions = List(reader.compactU16()) {
                SolanaInstruction(
                    programIndex = reader.byte(),
                    accounts = List(reader.compactU16()) { reader.byte() },
                    data = reader.bytes(reader.compactU16()),
                )
            }
            return SolanaMessage(keys, instructions)
        }

        private const val VERSIONED_PREFIX = 0x80
        private const val HEADER_SIZE = 3
        private const val KEY_SIZE = 32
    }
}

private class ByteReader(private val bytes: ByteArray) {
    private var position = 0

    fun byte(): Int = bytes[position++].toInt() and BYTE_MASK

    fun bytes(count: Int): ByteArray = bytes.copyOfRange(position, position + count).also { position += count }

    fun skip(count: Int) {
        bytes(count)
    }

    fun compactU16(): Int {
        var value = 0
        var shift = 0
        while (true) {
            val next = byte()
            value = value or ((next and SEVEN_BITS) shl shift)
            if (next and CONTINUATION == 0) return value
            shift += SEVEN_BIT_SHIFT
        }
    }

    private companion object {
        const val BYTE_MASK = 0xFF
        const val SEVEN_BITS = 0x7F
        const val CONTINUATION = 0x80
        const val SEVEN_BIT_SHIFT = 7
    }
}
