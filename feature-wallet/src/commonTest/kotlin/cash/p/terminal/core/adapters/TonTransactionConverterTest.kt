package cash.p.terminal.core.adapters

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
import io.horizontalsystems.tonkit.models.TonTransfer
import io.mockk.mockk
import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

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

    private fun converter(tokenType: TokenType, address: Address, version: TonWalletVersion): TonTransactionConverter {
        val token = Token(
            coin = Coin(uid = "toncoin", name = "Toncoin", code = "TON"),
            blockchain = Blockchain(BlockchainType.Ton, "The Open Network", null),
            type = tokenType,
            decimals = 9,
        )
        return TonTransactionConverter(
            address = address,
            coinManager = mockk(relaxed = true),
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
        const val W5_RAW = "0:2222222222222222222222222222222222222222222222222222222222222222"
    }
}
