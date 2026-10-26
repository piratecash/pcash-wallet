package cash.p.terminal.modules.balance.token.addresspoisoning

import cash.p.terminal.core.TransactionSettingsStorage
import cash.p.terminal.core.providers.SharedAppConfig
import cash.p.terminal.modules.transactions.TransactionViewItem
import cash.p.terminal.modules.transactions.poison_status.PoisonStatus
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.TransactionInfo_Sent
import cash.p.terminal.resources.Transactions_To
import cash.p.terminal.resources.coin_placeholder
import cash.p.terminal.strings.helpers.shorten
import cash.p.terminal.ui_compose.ColorName
import cash.p.terminal.ui_compose.ColoredValue
import cash.p.terminal.wallet.MarketKitWrapper
import cash.p.terminal.wallet.coinImageUrl
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.IAppNumberFormatter
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.BlockchainType
import java.math.BigDecimal
import java.util.Date

class AddressPoisoningViewModel(
    private val coinUid: String,
    isPirate: Boolean,
    blockchainType: BlockchainType,
    private val localStorage: TransactionSettingsStorage,
    private val marketKit: MarketKitWrapper,
    private val currencyManager: CurrencyManager,
    private val numberFormatter: IAppNumberFormatter,
) : ViewModelUiState<AddressPoisoningViewUiState>() {

    private val coin = marketKit.coin(coinUid)
    private val amount = if (isPirate) BigDecimal(150) else BigDecimal.ONE
    private val displayAddress = SharedAppConfig.donateAddresses[blockchainType]?.shorten() ?: MOCK_ADDRESS_FALLBACK
    private var selectedMode = localStorage.addressPoisoningViewMode

    override fun createState(): AddressPoisoningViewUiState {
        val currency = currencyManager.baseCurrency
        val coinPrice = marketKit.coinPrice(coinUid, currency.code)
        val fiatValue = coinPrice?.value?.let { price ->
            numberFormatter.formatFiatShort(amount * price, currency.symbol, currency.decimal)
        } ?: "---"

        val amountFormatted = "-${numberFormatter.formatCoinFull(amount, coin?.code, 0)}"
        val subtitle = Translator.getString(Res.string.Transactions_To, displayAddress)

        return AddressPoisoningViewUiState(
            selectedMode = selectedMode,
            standardItem = buildMockItem(amountFormatted, fiatValue, subtitle, AddressPoisoningViewMode.STANDARD),
            compactItem = buildMockItem(amountFormatted, fiatValue, subtitle, AddressPoisoningViewMode.COMPACT),
        )
    }

    private fun buildMockItem(
        amount: String,
        fiatValue: String,
        subtitle: String,
        mode: AddressPoisoningViewMode,
    ) = TransactionViewItem(
        uid = "preview_${mode.name}",
        progress = null,
        title = Translator.getString(Res.string.TransactionInfo_Sent),
        subtitle = subtitle,
        primaryValue = ColoredValue(amount, ColorName.Lucian),
        secondaryValue = ColoredValue(fiatValue, ColorName.Secondary),
        date = Date(),
        formattedTime = "14:30",
        icon = TransactionViewItem.Icon.Regular(
            url = coinImageUrl(coinUid),
            alternativeUrl = coin?.image,
            placeholder = Res.drawable.coin_placeholder,
        ),
        poisonStatus = PoisonStatus.CREATED,
        addressPoisoningViewMode = mode,
    )

    fun onSelect(mode: AddressPoisoningViewMode) {
        selectedMode = mode
        localStorage.addressPoisoningViewMode = mode
        emitState()
    }

    companion object {
        private val MOCK_ADDRESS_FALLBACK = "x".repeat(30).shorten()
    }
}

data class AddressPoisoningViewUiState(
    val selectedMode: AddressPoisoningViewMode,
    val standardItem: TransactionViewItem,
    val compactItem: TransactionViewItem,
)
