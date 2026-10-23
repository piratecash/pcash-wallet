package cash.p.terminal.modules.transactionInfo


import cash.p.terminal.core.managers.EvmKitWrapper
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.modules.offline.OperationAvailability
import cash.p.terminal.modules.transactions.AmlStatus
import cash.p.terminal.modules.transactions.TransactionStatus
import cash.p.terminal.ui_compose.ColorName
import cash.p.terminal.ui_compose.ColoredValue
import cash.p.terminal.wallet.Wallet
import io.horizontalsystems.core.entities.BlockchainType
import java.util.Date
import org.jetbrains.compose.resources.DrawableResource
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_language

sealed class TransactionInfoViewItem {
    class Transaction(val leftValue: String, val rightValue: String, val icon: DrawableResource?) :
        TransactionInfoViewItem()

    class Amount(
        val coinValue: ColoredValue,
        val fiatValue: ColoredValue,
        val coinIconUrl: String?,
        val alternativeCoinIconUrl: String?,
        val coinIconPlaceholder: DrawableResource?,
        val coinUid: String?,
        val badge: String?,
        val amountType: AmountType,
    ) : TransactionInfoViewItem()

    class NftAmount(
        val title: String,
        val nftValue: ColoredValue,
        val nftName: String?,
        val iconUrl: String?,
        val iconPlaceholder: DrawableResource?,
        val badge: String?,
    ) : TransactionInfoViewItem()

    class Value(val title: String, val value: String) : TransactionInfoViewItem()

    class ValueClickable(val title: String, val value: String) : TransactionInfoViewItem()

    class ValueColored(val title: String, val value: String, val color: ColorName) :
        TransactionInfoViewItem()

    class PriceWithToggle(val title: String, val valueOne: String, val valueTwo: String) :
        TransactionInfoViewItem()

    class Address(
        val title: String,
        val value: String,
        val showAdd: Boolean,
        val blockchainType: BlockchainType,
        val showCopyWarning: Boolean = false,
        val collapseAddress: Boolean = false,
    ) : TransactionInfoViewItem()

    class ContactItem(val contact: Contact) : TransactionInfoViewItem()

    class TransactionHash(val transactionHash: String) : TransactionInfoViewItem()

    class Explorer(
        val title: String,
        val url: String?,
        val iconResId: DrawableResource = Res.drawable.ic_language,
    ) : TransactionInfoViewItem()

    class Status(val status: TransactionStatus) : TransactionInfoViewItem()

    class OfflineStatus(val status: ColoredValue) : TransactionInfoViewItem()

    object RawTransaction : TransactionInfoViewItem()

    class LockState(
        val title: String,
        val leftIcon: DrawableResource,
        val date: Date,
        val showLockInfo: Boolean
    ) : TransactionInfoViewItem()

    class DoubleSpend(val transactionHash: String, val conflictingHash: String) :
        TransactionInfoViewItem()

    object SentToSelf : TransactionInfoViewItem()

    class SpeedUpCancel(
        val transactionHash: String,
        val blockchainType: BlockchainType,
        val availability: OperationAvailability,
        val wallet: Wallet?,
        val evmKitWrapper: EvmKitWrapper?,
    ) : TransactionInfoViewItem()

    class BeamCancel(
        val transactionHash: String,
        val wallet: Wallet?,
        val availability: OperationAvailability,
    ) : TransactionInfoViewItem()

    class WarningMessage(val message: String) : TransactionInfoViewItem()

    object PoisonWarning : TransactionInfoViewItem()

    class Description(val text: String) : TransactionInfoViewItem()

    class AmlCheck(
        val status: AmlStatus,
        val senderAddresses: List<String>,
        val blockchainType: BlockchainType
    ) : TransactionInfoViewItem()
}

enum class AmountType {
    YouSent, YouGot, Received, Sent, Approved;
}
