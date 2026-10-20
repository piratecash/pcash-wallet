package cash.p.terminal.premium.data

import cash.p.terminal.premium.domain.HardwareBnbAddressProvider
import cash.p.terminal.tangem.common.CustomXPubKeyAddressParser
import cash.p.terminal.tangem.domain.usecase.BuildHardwarePublicKeyUseCase
import cash.p.terminal.tangem.domain.usecase.TangemScanUseCase
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.entities.TokenQuery
import com.tangem.common.doOnFailure
import com.tangem.common.doOnSuccess
import io.horizontalsystems.core.toHexStringWithLeadingZero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.bouncycastle.jcajce.provider.digest.Keccak
import kotlin.coroutines.resume

internal class TangemBnbAddressProvider(
    private val tangemScanUseCase: TangemScanUseCase
) : HardwareBnbAddressProvider {

    override suspend fun scanAddress(account: Account): String? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                launch {
                    val blockchainTypesToDerive = listOf(TokenQuery.PirateCashBnb)
                    tangemScanUseCase.scanProduct(
                        blockchainsToDerive = blockchainTypesToDerive
                    ).doOnSuccess { scanResponse ->
                        val publicKeys =
                            BuildHardwarePublicKeyUseCase().invoke(
                                scanResponse = scanResponse,
                                accountId = account.id,
                                blockchainTypeList = blockchainTypesToDerive
                            )
                        val firstKey = publicKeys.firstOrNull()
                        if (firstKey != null) {
                            if (continuation.isActive) continuation.resume(buildEvmAddress(firstKey.key.value))
                        } else {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }.doOnFailure {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
        }

    private fun buildEvmAddress(xPubKey: String): String {
        var raw =
            CustomXPubKeyAddressParser.parse(xPubKey).addressBytes
        if (raw.size == 32) {
            raw = raw.copyOfRange(12, raw.size)
        }
        return toChecksumAddress(raw.toHexStringWithLeadingZero())
    }

    private fun toChecksumAddress(address: String): String {
        val cleanAddress = address.removePrefix("0x").lowercase()
        val keccak = Keccak.Digest256()
        val hash = keccak.digest(cleanAddress.toByteArray())

        val result = StringBuilder("0x")
        for (i in cleanAddress.indices) {
            val char = cleanAddress[i]
            if (char.isLetter() && (hash[i / 2].toInt() and (if (i % 2 == 0) 0x80 else 0x08)) != 0) {
                result.append(char.uppercaseChar())
            } else {
                result.append(char)
            }
        }
        return result.toString()
    }
}
