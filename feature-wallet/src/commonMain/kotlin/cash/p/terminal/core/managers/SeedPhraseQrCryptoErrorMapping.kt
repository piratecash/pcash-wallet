package cash.p.terminal.core.managers

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.seed_qr_decryption_failed
import cash.p.terminal.resources.seed_qr_invalid_format
import io.horizontalsystems.core.Translator

fun Throwable.toSeedQrErrorMessage(): String = Translator.getString(
    when (this) {
        is SeedPhraseQrCrypto.QrDecodeError.InvalidFormat -> Res.string.seed_qr_invalid_format
        else -> Res.string.seed_qr_decryption_failed
    }
)
