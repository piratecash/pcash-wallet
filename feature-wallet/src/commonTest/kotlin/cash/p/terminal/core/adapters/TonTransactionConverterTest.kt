package cash.p.terminal.core.adapters

import cash.p.terminal.core.ICoinManager
import cash.p.terminal.entities.TransactionValue
import cash.p.terminal.entities.transactionrecords.LiquidityOperation
import cash.p.terminal.entities.transactionrecords.ton.TonTransactionRecord
import cash.p.terminal.wallet.Account
import cash.p.terminal.wallet.AccountOrigin
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Token
import cash.p.terminal.wallet.entities.Coin
import cash.p.terminal.wallet.entities.TokenType
import cash.p.terminal.wallet.entities.TonWalletVersion
import cash.p.terminal.wallet.meta
import cash.p.terminal.wallet.transaction.TransactionSource
import io.horizontalsystems.core.entities.Blockchain
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.tonkit.Address
import io.horizontalsystems.tonkit.models.AccountAddress
import io.horizontalsystems.tonkit.models.Action
import io.horizontalsystems.tonkit.models.Event
import io.horizontalsystems.tonkit.models.Jetton
import io.horizontalsystems.tonkit.models.JettonBurn
import io.horizontalsystems.tonkit.models.JettonSwap
import io.horizontalsystems.tonkit.models.JettonTransfer
import io.horizontalsystems.tonkit.models.JettonVerificationType
import io.horizontalsystems.tonkit.models.LiquidityDeposit
import io.horizontalsystems.tonkit.models.SmartContractExec
import io.horizontalsystems.tonkit.models.TonTransfer
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class TonTransactionConverterTest {

    private val account = Account(
        id = "account-id",
        name = "Account",
        type = AccountType.Mnemonic(listOf("word"), ""),
        origin = AccountOrigin.Created,
        level = 0,
    )
    private val v4r2Address = Address.parse(V4R2_RAW)
    private val w5Address = Address.parse(W5_RAW)

    @Test
    fun createTransactionRecord_ownTransferV4r2ToW5_recordsKeepSeparateUidsAndSameHash() {
        val event = transferEvent(from = v4r2Address, to = w5Address)

        val v4r2Record = converter(TokenType.Native, v4r2Address, TonWalletVersion.V4R2)
            .createTransactionRecord(event)
        val w5Record = converter(TokenType.TonW5Native, w5Address, TonWalletVersion.W5)
            .createTransactionRecord(event)

        assertEquals(EVENT_ID, v4r2Record.uid)
        assertNotEquals(v4r2Record.uid, w5Record.uid)
        assertEquals(EVENT_ID, v4r2Record.transactionHash)
        assertEquals(EVENT_ID, w5Record.transactionHash)
        assertIs<TonTransactionRecord.Action.Type.Send>(v4r2Record.actions.single().type)
        assertIs<TonTransactionRecord.Action.Type.Receive>(w5Record.actions.single().type)
    }


    @Test
    fun createTransactionRecord_stonfiV2Deposit_isAddWithBothDeposits() {
        val record = liquidityRecord(
            action(
                Action.Type.LiquidityDeposit,
                liquidityDeposit = LiquidityDeposit(
                    protocol = "stonfi",
                    from = own,
                    tokens = listOf(
                        LiquidityDeposit.Token(BigInteger.valueOf(2_000_000_000L), null, Address.parse(VAULT_TON)),
                        LiquidityDeposit.Token(BigInteger.valueOf(5_000_000L), usdt, Address.parse(VAULT_USDT)),
                    ),
                ),
            )
        )

        val type = assertIs<TonTransactionRecord.Action.Type.LiquidityDeposit>(record.actions.single().type)
        assertEquals(LiquidityOperation(LiquidityOperation.Direction.Add, failed = false), record.liquidityOperation)
        assertEquals("stonfi", type.protocol)
        assertEquals(2, type.deposits.size)
        assertIs<TransactionValue.JettonValue>(type.deposits[1].value)
    }

    @Test
    fun createTransactionRecord_failedStonfiV2Deposit_isFailedAdd() {
        val record = liquidityRecord(
            action(
                Action.Type.LiquidityDeposit,
                status = Action.Status.FAILED,
                liquidityDeposit = LiquidityDeposit(
                    "stonfi", own, listOf(LiquidityDeposit.Token(BigInteger.ONE, null, Address.parse(VAULT_TON)))
                ),
            )
        )

        assertEquals(LiquidityOperation(LiquidityOperation.Direction.Add, failed = true), record.liquidityOperation)
    }

    @Test
    fun createTransactionRecord_depositFromForeignAddress_staysUnsupported() {
        val record = liquidityRecord(
            action(
                Action.Type.LiquidityDeposit,
                liquidityDeposit = LiquidityDeposit(
                    "stonfi", other, listOf(LiquidityDeposit.Token(BigInteger.ONE, null, Address.parse(VAULT_TON)))
                ),
            )
        )

        assertIs<TonTransactionRecord.Action.Type.Unsupported>(record.actions.single().type)
        assertNull(record.liquidityOperation)
    }

    @Test
    fun createTransactionRecord_stonfiV1ProvideComment_isAdd() {
        val record = liquidityRecord(sendJetton(comment = "Call: StonfiProvideLiquidity"))

        assertEquals(LiquidityOperation(LiquidityOperation.Direction.Add, failed = false), record.liquidityOperation)
    }

    @Test
    fun createTransactionRecord_dedustDepositComment_isAdd() {
        val record = liquidityRecord(sendJetton(comment = "Call: DedustDepositLiquidity"))

        assertEquals(LiquidityOperation(LiquidityOperation.Direction.Add, failed = false), record.liquidityOperation)
    }

    @Test
    fun createTransactionRecord_failedDedustSmartContractDeposit_isFailedAdd() {
        val record = liquidityRecord(
            action(
                Action.Type.SmartContract,
                status = Action.Status.FAILED,
                smartContractExec = SmartContractExec(other, BigInteger.ONE, "DedustDepositLiquidity", null),
            )
        )

        assertEquals(LiquidityOperation(LiquidityOperation.Direction.Add, failed = true), record.liquidityOperation)
    }

    @Test
    fun createTransactionRecord_jettonTransferWithOtherComment_isNotLabelled() {
        val record = liquidityRecord(sendJetton(comment = "thanks"))

        assertNull(record.liquidityOperation)
    }

    @Test
    fun createTransactionRecord_burnAndIncomingTransfers_isRemove() {
        val record = liquidityRecord(
            burn(),
            receiveJetton(),
            receiveJetton(),
            tonTransfer(from = other, to = own),
        )

        assertEquals(
            LiquidityOperation(LiquidityOperation.Direction.Remove, failed = false),
            record.liquidityOperation
        )
    }

    @Test
    fun createTransactionRecord_burnWithoutIncomingTransfer_isNotLabelled() {
        val record = liquidityRecord(burn())

        assertNull(record.liquidityOperation)
    }

    @Test
    fun createTransactionRecord_lpSentToZeroAddress_isNotLabelled() {
        val record = liquidityRecord(sendJetton(comment = null), receiveJetton())

        assertNull(record.liquidityOperation)
    }

    @Test
    fun createTransactionRecord_incomingTransfersWithoutBurn_isNotLabelled() {
        val record = liquidityRecord(receiveJetton(), receiveJetton())

        assertNull(record.liquidityOperation)
    }

    @Test
    fun createTransactionRecord_burnWithZeroAmount_isNotLabelled() {
        val record = liquidityRecord(burn(amount = BigInteger.ZERO), receiveJetton())

        assertNull(record.liquidityOperation)
    }

    @Test
    fun createTransactionRecord_failedBurn_isNotLabelled() {
        val record = liquidityRecord(burn(status = Action.Status.FAILED), receiveJetton())

        assertNull(record.liquidityOperation)
    }

    @Test
    fun createTransactionRecord_twoBurns_isNotLabelled() {
        val record = liquidityRecord(burn(), burn(), receiveJetton())

        assertNull(record.liquidityOperation)
    }

    @Test
    fun createTransactionRecord_burnOfForeignWallet_isNotLabelled() {
        val record = liquidityRecord(burn(sender = other), receiveJetton())

        assertNull(record.liquidityOperation)
    }

    @Test
    fun createTransactionRecord_jettonSwap_isNotLabelled() {
        val record = liquidityRecord(
            action(
                Action.Type.JettonSwap,
                jettonSwap = JettonSwap(
                    dex = "stonfi",
                    amountIn = BigInteger.TEN,
                    amountOut = BigInteger.TEN,
                    tonIn = null,
                    tonOut = BigInteger.ONE,
                    userWallet = own,
                    router = other,
                    jettonMasterIn = usdt,
                    jettonMasterOut = null,
                ),
            )
        )

        assertNull(record.liquidityOperation)
    }

    @Test
    fun liquidityValues_removeWithTwoReceives_returnsBothAsIncomingWithoutLpBurn() {
        val record = liquidityRecord(burn(), receiveJetton(amount = 7), receiveJetton(amount = 3))

        val (incoming, outgoing) = record.liquidityValues()

        assertEquals(1, incoming.size)
        assertEquals(0, BigDecimal("0.00001").compareTo(incoming.single().decimalValue))
        assertEquals(emptyList(), outgoing)
    }

    private val own = AccountAddress(v4r2Address, name = null, isScam = false, isWallet = true)
    private val other = AccountAddress(w5Address, name = null, isScam = false, isWallet = false)
    private val usdt = Jetton(Address.parse(USDT_RAW), "Tether", "USDT", 6, null, JettonVerificationType.NONE)

    private fun liquidityRecord(vararg actions: Action): TonTransactionRecord {
        val coinManager = mockk<ICoinManager>()
        every { coinManager.getToken(any()) } returns null
        val event = Event("e", 1L, 1_715_000_000L, false, false, 0L, actions.toList())
        return converter(TokenType.Native, v4r2Address, TonWalletVersion.V4R2, coinManager)
            .createTransactionRecord(event)
    }

    private fun action(
        type: Action.Type,
        status: Action.Status = Action.Status.OK,
        tonTransfer: TonTransfer? = null,
        jettonTransfer: JettonTransfer? = null,
        jettonBurn: JettonBurn? = null,
        jettonSwap: JettonSwap? = null,
        smartContractExec: SmartContractExec? = null,
        liquidityDeposit: LiquidityDeposit? = null,
    ) = Action(
        type, status, tonTransfer, jettonTransfer, jettonBurn, null, null, jettonSwap, smartContractExec,
        liquidityDeposit
    )

    private fun tonTransfer(from: AccountAddress, to: AccountAddress) = action(
        Action.Type.TonTransfer,
        tonTransfer = TonTransfer(from, to, BigInteger.valueOf(1_000_000_000L), null),
    )

    private fun sendJetton(comment: String?) = action(
        Action.Type.JettonTransfer,
        jettonTransfer = JettonTransfer(own, other, v4r2Address, w5Address, BigInteger.TEN, comment, usdt),
    )

    private fun receiveJetton(amount: Long = 10) = action(
        Action.Type.JettonTransfer,
        jettonTransfer = JettonTransfer(other, own, w5Address, v4r2Address, BigInteger.valueOf(amount), null, usdt),
    )

    private fun burn(
        sender: AccountAddress = own,
        amount: BigInteger = BigInteger.TEN,
        status: Action.Status = Action.Status.OK,
    ) = action(
        Action.Type.JettonBurn,
        status = status,
        jettonBurn = JettonBurn(sender, v4r2Address, amount, usdt),
    )

    private fun converter(
        tokenType: TokenType,
        address: Address,
        version: TonWalletVersion,
        coinManager: ICoinManager = mockk(relaxed = true),
    ): TonTransactionConverter {
        val token = Token(
            coin = Coin(uid = "toncoin", name = "Toncoin", code = "TON"),
            blockchain = Blockchain(BlockchainType.Ton, "The Open Network", null),
            type = tokenType,
            decimals = 9,
        )
        return TonTransactionConverter(
            address = address,
            coinManager = coinManager,
            source = TransactionSource(token.blockchain, account, tokenType.meta),
            baseToken = token,
            walletVersion = version,
        )
    }

    private fun transferEvent(from: Address, to: Address) = Event(
        id = EVENT_ID,
        lt = 1L,
        timestamp = 1_715_000_000L,
        scam = false,
        inProgress = false,
        extra = 0L,
        actions = listOf(
            Action(
                type = Action.Type.TonTransfer,
                status = Action.Status.OK,
                tonTransfer = TonTransfer(
                    sender = AccountAddress(from, name = null, isScam = false, isWallet = true),
                    recipient = AccountAddress(to, name = null, isScam = false, isWallet = true),
                    amount = BigInteger.valueOf(1_000_000_000L),
                    comment = null,
                ),
                jettonTransfer = null,
                jettonBurn = null,
                jettonMint = null,
                contractDeploy = null,
                jettonSwap = null,
                smartContractExec = null,
            )
        )
    )

    private companion object {
        const val EVENT_ID = "ton-event-id"
        const val V4R2_RAW = "0:1111111111111111111111111111111111111111111111111111111111111111"
        const val USDT_RAW = "0:3333333333333333333333333333333333333333333333333333333333333333"
        const val VAULT_TON = "0:4444444444444444444444444444444444444444444444444444444444444444"
        const val VAULT_USDT = "0:5555555555555555555555555555555555555555555555555555555555555555"
        const val W5_RAW = "0:2222222222222222222222222222222222222222222222222222222222222222"
    }
}
