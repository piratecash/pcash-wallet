package cash.p.terminal.trezor.signer

import cash.p.terminal.trezorkit.client.TrezorTypedData
import cash.p.terminal.trezorkit.client.TrezorTypedMember
import cash.p.terminal.trezorkit.client.TrezorTypedValue
import org.web3j.crypto.StructuredData

internal const val EIP712_DOMAIN = "EIP712Domain"

internal fun StructuredData.EIP712Message.toTrezorTypedData(): TrezorTypedData {
    val trezorTypes = types.mapValues { (_, entries) -> entries.map { TrezorTypedMember(it.name, it.type) } }
    val domainFields = trezorTypes[EIP712_DOMAIN].orEmpty().mapNotNull { member ->
        domain.valueOf(member.name)?.let { member.name to TrezorTypedValue.Primitive(it) }
    }
    return TrezorTypedData(
        primaryType = primaryType,
        types = trezorTypes,
        domain = TrezorTypedValue.Struct(domainFields.toMap()),
        message = (message as? Map<*, *>)?.toStruct() ?: TrezorTypedValue.Struct(emptyMap()),
    )
}

private fun StructuredData.EIP712Domain.valueOf(member: String): String? = when (member) {
    "name" -> name
    "version" -> version
    "chainId" -> chainId
    "verifyingContract" -> verifyingContract
    "salt" -> salt
    else -> null
}

private fun Map<*, *>.toStruct(): TrezorTypedValue.Struct =
    TrezorTypedValue.Struct(entries.associate { (key, value) -> key.toString() to value.toTrezorTypedValue() })

// Jackson yields maps, lists, strings, numbers and booleans; the latter two keep their JSON text.
private fun Any?.toTrezorTypedValue(): TrezorTypedValue = when (this) {
    is Map<*, *> -> toStruct()
    is List<*> -> TrezorTypedValue.Array(map { it.toTrezorTypedValue() })
    else -> TrezorTypedValue.Primitive(toString())
}
