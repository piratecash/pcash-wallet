package cash.p.terminal.modules.btcblockchainsettings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import cash.p.terminal.core.AppearanceSettingsStore
import cash.p.terminal.entities.BtcRestoreMode
import cash.p.terminal.modules.btcblockchainsettings.BtcBlockchainSettingsModule.BlockchainSettingsIcon
import cash.p.terminal.modules.btcblockchainsettings.BtcBlockchainSettingsModule.ViewItem
import cash.p.terminal.resources.Res
import cash.p.terminal.resources.ic_api_hybrid
import cash.p.terminal.resources.ic_blockchair
import io.horizontalsystems.core.Translator
import io.horizontalsystems.core.ViewModelUiState
import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.core.imageUrl
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow

internal class BtcBlockchainSettingsViewModel(
    private val service: BtcBlockchainSettingsService,
    private val localStorage: AppearanceSettingsStore,
) : ViewModelUiState<BtcBlockchainSettingsUIState>() {

    private val isCustomPeersEnabled = service.blockchain.type == BlockchainType.Dash

    private var closeScreen = false
    private var restoreSources = emptyList<ViewItem>()
    private var saveButtonEnabled = false
    private var customPeers: String? = null

    override fun createState() = BtcBlockchainSettingsUIState(
        title = service.blockchain.name,
        blockchainIconUrl = service.blockchain.type.imageUrl,
        restoreSources = restoreSources,
        saveButtonEnabled = saveButtonEnabled || isCustomPeersChanged(),
        closeScreen = closeScreen,
        customPeers = customPeers
    )

    init {
        viewModelScope.launch {
            service.hasChangesObservable.asFlow().collect {
                saveButtonEnabled = it
                syncRestoreModeState()
                emitState()
            }
        }

        if (isCustomPeersEnabled) {
            customPeers = localStorage.customDashPeers
        }

        syncRestoreModeState()
        emitState()
    }

    fun onSelectRestoreMode(viewItem: ViewItem) {
        service.setRestoreMode(viewItem.id)
    }

    fun onCustomPeersChange(peers: String) {
        customPeers = peers
        emitState()
    }

    private fun isCustomPeersChanged(): Boolean {
        return isCustomPeersEnabled && customPeers != localStorage.customDashPeers
    }

    fun onSaveClick() {
        service.save(isCustomPeersChanged())
        closeScreen = true
        if (isCustomPeersEnabled) {
            localStorage.customDashPeers = customPeers.orEmpty()
        }
        emitState()
    }

    private fun syncRestoreModeState() {
        val viewItems = service.restoreModes.map { mode ->
            ViewItem(
                id = mode.raw,
                title = Translator.getString(mode.getTitle(service.blockchain.type)),
                subtitle = Translator.getString(mode.description),
                selected = mode == service.restoreMode,
                icon = mode.icon
            )
        }
        restoreSources = viewItems
        emitState()
    }

    private val BtcRestoreMode.icon: BlockchainSettingsIcon
        get() = when (this) {
            BtcRestoreMode.Blockchair -> {

                when (service.blockchain.type) {
                    BlockchainType.Cosanta,
                    BlockchainType.PirateCash -> {
                        BlockchainSettingsIcon.BlockchainIcon(service.blockchain.type.imageUrl)
                    }

                    else -> {
                        BlockchainSettingsIcon.ApiIcon(Res.drawable.ic_blockchair)
                    }
                }
            }

            BtcRestoreMode.Hybrid -> BlockchainSettingsIcon.ApiIcon(Res.drawable.ic_api_hybrid)
            BtcRestoreMode.Blockchain -> BlockchainSettingsIcon.BlockchainIcon(service.blockchain.type.imageUrl)
        }

}

@Immutable
internal data class BtcBlockchainSettingsUIState(
    val title: String,
    val blockchainIconUrl: String,
    val restoreSources: List<ViewItem>,
    val saveButtonEnabled: Boolean,
    val closeScreen: Boolean,
    val customPeers: String?
)
