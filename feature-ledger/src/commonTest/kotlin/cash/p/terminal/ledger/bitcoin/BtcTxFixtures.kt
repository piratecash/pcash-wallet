package cash.p.terminal.ledger.bitcoin

import cash.p.terminal.wallet.entities.HardwarePublicKey
import cash.p.terminal.wallet.entities.HardwarePublicKeyType
import cash.p.terminal.wallet.entities.SecretString
import cash.p.terminal.wallet.entities.TokenQuery
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.hardware.HardwareKeySpec
import io.horizontalsystems.bitcoincore.extensions.hexToByteArray
import io.horizontalsystems.bitcoincore.extensions.toHexString
import io.horizontalsystems.bitcoincore.models.AddressType
import io.horizontalsystems.bitcoincore.models.LegacyAddress
import io.horizontalsystems.bitcoincore.models.PublicKey
import io.horizontalsystems.bitcoincore.models.Transaction
import io.horizontalsystems.bitcoincore.models.TransactionInput
import io.horizontalsystems.bitcoincore.models.TransactionOutput
import io.horizontalsystems.bitcoincore.serializers.BaseTransactionSerializer
import io.horizontalsystems.bitcoincore.storage.FullTransaction
import io.horizontalsystems.bitcoincore.storage.InputToSign
import io.horizontalsystems.bitcoincore.transactions.builder.MutableTransaction
import io.horizontalsystems.bitcoincore.transactions.scripts.OpCodes
import io.horizontalsystems.bitcoincore.transactions.scripts.ScriptType
import io.horizontalsystems.bitcoincore.utils.Utils
import io.horizontalsystems.core.entities.BlockchainType

/** Kit-shaped transactions with real scripts, so the device and the kit's own serializer accept them. */
object BtcTxFixtures {
    val serializer = BaseTransactionSerializer()

    val bitcoinBip44 = TokenQuery(BlockchainType.Bitcoin, TokenType.Derived(TokenType.Derivation.Bip44))
    val bitcoinBip84 = TokenQuery(BlockchainType.Bitcoin, TokenType.Derived(TokenType.Derivation.Bip84))
    val bitcoinBip49 = TokenQuery(BlockchainType.Bitcoin, TokenType.Derived(TokenType.Derivation.Bip49))
    val bitcoinBip86 = TokenQuery(BlockchainType.Bitcoin, TokenType.Derived(TokenType.Derivation.Bip86))
    val litecoinBip44 = TokenQuery(BlockchainType.Litecoin, TokenType.Derived(TokenType.Derivation.Bip44))
    val litecoinBip84 = TokenQuery(BlockchainType.Litecoin, TokenType.Derived(TokenType.Derivation.Bip84))
    val bitcoinCash = TokenQuery(BlockchainType.BitcoinCash, TokenType.AddressTyped(TokenType.AddressType.Type145))
    val dogecoin = TokenQuery(BlockchainType.Dogecoin, TokenType.Native)

    fun spec(query: TokenQuery): HardwareKeySpec = checkNotNull(LedgerBtcApp.specFor(query)).first

    fun storedKey(query: TokenQuery, accountId: String, key: String) = HardwarePublicKey(
        accountId = accountId,
        blockchainType = query.blockchainType.uid,
        type = HardwarePublicKeyType.PUBLIC_KEY,
        tokenType = query.tokenType,
        key = SecretString(key),
        derivationPath = spec(query).derivationPath,
        publicKey = byteArrayOf(1),
        derivedPublicKey = byteArrayOf(1),
    )

    fun key(publicKeyHex: String, external: Boolean = true, index: Int = 0): PublicKey =
        key(publicKeyHex.hexToByteArray(), external, index)

    /** Built like the kit builds its keys, so taproot keys carry their tweaked output key. */
    fun key(publicKey: ByteArray, external: Boolean, index: Int = 0): PublicKey =
        PublicKey(0, index, external, publicKey, Utils.sha256Hash160(publicKey))

    fun lockingScript(type: ScriptType, key: PublicKey): ByteArray = when (type) {
        ScriptType.P2PKH -> OpCodes.p2pkhStart + OpCodes.push(key.publicKeyHash) + OpCodes.p2pkhEnd
        ScriptType.P2WPKH -> OpCodes.scriptWPKH(key.publicKeyHash)
        ScriptType.P2WPKHSH ->
            OpCodes.p2pshStart + OpCodes.push(Utils.sha256Hash160(OpCodes.scriptWPKH(key.publicKeyHash))) + OpCodes.p2pshEnd
        ScriptType.P2TR -> byteArrayOf(0x51, 0x20) + key.convertedForP2TR
        else -> byteArrayOf(OP_HASH160) + OpCodes.push(ByteArray(20)) + byteArrayOf(OP_EQUAL)
    }

    /** A transaction paying [value] to [key]; its serialized form is what the kit's storage returns. */
    fun funding(type: ScriptType, key: PublicKey, value: Long): FullTransaction = FullTransaction(
        header = Transaction(version = 1, lockTime = 0),
        inputs = listOf(TransactionInput(ByteArray(32) { 7 }, 0L, byteArrayOf(0x51), 0xFFFFFFFFL)),
        outputs = listOf(TransactionOutput(value, 0, lockingScript(type, key), type)),
    )

    fun rawHex(transaction: FullTransaction): String = serializer.serialize(transaction).toHexString()

    fun spend(funding: FullTransaction, type: ScriptType, key: PublicKey) = InputToSign(
        input = TransactionInput(funding.header.hash, 0L, sequence = 0xFFFFFFFDL),
        previousOutput = funding.outputs.single(),
        previousOutputPublicKey = key,
    )

    fun output(value: Long, script: ByteArray, address: String) =
        TransactionOutput(value, 0, script, ScriptType.UNKNOWN, address)

    fun mutableTransaction(
        inputs: List<InputToSign>,
        outputs: List<TransactionOutput>,
        changeKey: PublicKey? = null,
    ) = MutableTransaction().apply {
        inputs.forEach(::addInput)
        this.outputs = outputs
        changeKey?.let { key ->
            changeAddress = LegacyAddress(CHANGE_ADDRESS, key.publicKeyHash, AddressType.PubKeyHash)
            changePublicKey = key
        }
    }

    const val CHANGE_ADDRESS = "change-address"
    const val RECIPIENT_ADDRESS = "recipient-address"

    private const val OP_HASH160: Byte = 0xA9.toByte()
    private const val OP_EQUAL: Byte = 0x87.toByte()
}
