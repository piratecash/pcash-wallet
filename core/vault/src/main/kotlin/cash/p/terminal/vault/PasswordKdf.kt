package cash.p.terminal.vault

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

class Argon2Params private constructor(val memoryKib: Int, val iterations: Int, val parallelism: Int) {
    companion object {
        val DEFAULT = Argon2Params(memoryKib = 262_144, iterations = 3, parallelism = 1)

        // Upper bound is 2x DEFAULT: Argon2 runs before the tag check, so a tampered file must not cost more.
        private const val MAX_MEMORY_KIB = 524_288

        internal fun ofOrNull(memoryKib: Int, iterations: Int, parallelism: Int): Argon2Params? {
            val valid = parallelism in 1..4 &&
                iterations in 1..10 &&
                memoryKib in 8 * parallelism..MAX_MEMORY_KIB
            return if (valid) Argon2Params(memoryKib, iterations, parallelism) else null
        }
    }
}

private const val KEK_SIZE = 32

internal fun deriveKek(password: ByteArray, salt: ByteArray, params: Argon2Params): ByteArray {
    val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
        .withMemoryAsKB(params.memoryKib)
        .withIterations(params.iterations)
        .withParallelism(params.parallelism)
        .withSalt(salt)
        .build()
    val generator = Argon2BytesGenerator().apply { init(parameters) }
    return ByteArray(KEK_SIZE).also { generator.generateBytes(password, it) }
}

// Sized for the worst case so the encoder never reallocates and leaves an unwiped copy behind.
internal fun CharArray.toUtf8Bytes(): ByteArray {
    val encoder = Charsets.UTF_8.newEncoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    val buffer = ByteBuffer.allocate((size * encoder.maxBytesPerChar()).toInt())
    try {
        encoder.encode(CharBuffer.wrap(this), buffer, true)
        encoder.flush(buffer)
        return buffer.array().copyOf(buffer.position())
    } finally {
        buffer.array().fill(0)
    }
}
