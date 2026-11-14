package cash.p.terminal.entities

import cash.p.terminal.core.factories.uriScheme
import cash.p.terminal.core.managers.EvmBlockchainManager
import cash.p.terminal.core.supported
import io.horizontalsystems.core.entities.BlockchainType
import java.math.BigDecimal

private const val MAX_AMOUNT_INTEGER_DIGITS = 64
private const val MAX_AMOUNT_FRACTION_DIGITS = 64

class AddressUri(
    val scheme: String,
) {
    var address: String = ""

    var parameters: MutableMap<Field, String> = mutableMapOf()
    var unhandledParameters: MutableMap<String, String> = mutableMapOf()

    inline fun <reified T> value(field: Field): T? {
        val value: Any? = when (T::class) {
            String::class -> parameters[field]
            BigDecimal::class -> parameters[field]?.toBigDecimalOrNull()
            Int::class -> parameters[field]?.toInt()
            else -> null
        }

        @Suppress("UNCHECKED_CAST")
        return value as? T
    }

    val amount: BigDecimal?
        get() = (value<BigDecimal>(Field.Amount) ?: value(Field.Value) ?: moneroAmount)?.takeIf { it.isDisplayable() }

    private val moneroAmount: BigDecimal?
        get() = if (scheme.equals("monero", ignoreCase = true)) value(Field.TxAmount) else null

    // "1e2147483647" parses fine but its plain form does not fit in memory; Long avoids Int overflow.
    private fun BigDecimal.isDisplayable(): Boolean =
        precision().toLong() - scale() <= MAX_AMOUNT_INTEGER_DIGITS && scale() <= MAX_AMOUNT_FRACTION_DIGITS


    enum class Field(val value: String) {
        Amount("amount"),
        Value("value"),
        TxAmount("tx_amount"),
        Label("label"),
        Message("message"),
        IS("IS"),
        Memo("memo"),
        Tag("tag"),
        BlockchainUid("blockchain_uid"),
        TokenUid("token_uid");

        companion object {
            fun amountField(blockchainType: BlockchainType): Field {
                return if (EvmBlockchainManager.blockchainTypes.contains(blockchainType)) {
                    Value
                } else {
                    Amount
                }
            }
        }
    }

    val allowedBlockchainTypes: List<BlockchainType>?
        get() {
            val concreteUid: String? = value(Field.BlockchainUid)
            concreteUid?.let {
                return listOf(BlockchainType.fromUid(concreteUid))
            }

            val type: BlockchainType? = BlockchainType.supported.firstOrNull { it.uriScheme == scheme }
            type?.let {
                return if (EvmBlockchainManager.blockchainTypes.contains(type)) {
                    EvmBlockchainManager.blockchainTypes
                } else {
                    listOf(type)
                }
            }

            return null
        }
}
