package cash.p.terminal.shared.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import cash.p.terminal.core.managers.BaseTokenManager
import cash.p.terminal.feature.wallet.BackupFileLauncher
import cash.p.terminal.feature.wallet.BackupFileSource
import cash.p.terminal.modules.backuplocal.fullbackup.AppSettingsBackup
import cash.p.terminal.modules.backuplocal.fullbackup.AppearanceSettingsBackup
import cash.p.terminal.modules.backuplocal.fullbackup.BlockchainSettingsBackup
import cash.p.terminal.modules.backuplocal.fullbackup.Settings
import cash.p.terminal.modules.backuplocal.fullbackup.restoreChartIndicators
import cash.p.terminal.modules.backuplocal.fullbackup.toChartIndicators
import cash.p.terminal.modules.chart.ChartIndicatorManager
import cash.p.terminal.modules.contacts.ContactsBackup
import cash.p.terminal.modules.contacts.ContactsSettingsBackup
import io.horizontalsystems.core.CurrencyManager
import io.horizontalsystems.core.ILanguageManager
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Desktop has no app icon. */
class DesktopAppSettingsBackup(
    private val languageManager: ILanguageManager,
    private val currencyManager: CurrencyManager,
    private val chartIndicatorManager: ChartIndicatorManager,
    private val chartIndicatorStorage: DesktopChartIndicatorStorage,
    private val baseTokenManager: BaseTokenManager,
    private val appearanceSettingsBackup: AppearanceSettingsBackup,
    private val blockchainSettingsBackup: BlockchainSettingsBackup,
    contactsSettingsBackup: ContactsSettingsBackup,
) : AppSettingsBackup, ContactsBackup by contactsSettingsBackup {

    override fun currentSettings(cachedKey: ByteArray, passphrase: String): Settings = Settings(
        balanceViewType = appearanceSettingsBackup.balanceViewType,
        appIcon = null,
        currentTheme = appearanceSettingsBackup.theme,
        chartIndicatorsEnabled = chartIndicatorManager.isEnabled,
        chartIndicators = chartIndicatorStorage.all().toChartIndicators(),
        balanceAutoHidden = appearanceSettingsBackup.balanceAutoHidden,
        conversionTokenQueryId = baseTokenManager.token?.tokenQuery?.id,
        language = languageManager.currentLocaleTag,
        launchScreen = appearanceSettingsBackup.launchScreen,
        marketsTabEnabled = appearanceSettingsBackup.marketsTabEnabled,
        balanceHideButtons = appearanceSettingsBackup.balanceHideButtons,
        baseCurrency = currencyManager.baseCurrency.code,
        btcModes = blockchainSettingsBackup.btcModes(),
        priceChangeMode = appearanceSettingsBackup.priceChangeMode,
        evmSyncSources = blockchainSettingsBackup.evmSyncSources(cachedKey, passphrase),
        solanaSyncSource = blockchainSettingsBackup.solanaSyncSource(),
    )

    override suspend fun restore(settings: Settings, passphrase: String) {
        appearanceSettingsBackup.restore(settings)
        languageManager.currentLocaleTag = settings.language
        settings.restoreChartIndicators(chartIndicatorManager, chartIndicatorStorage::insertAll)
        settings.conversionTokenQueryId?.let { baseTokenManager.setBaseTokenQueryId(it) }
        currencyManager.setBaseCurrencyCode(settings.baseCurrency)
        blockchainSettingsBackup.restore(settings, passphrase)
    }

    override fun customRpcCount(): Int? = blockchainSettingsBackup.customRpcCount()
}

/** AWT file dialogs: modal, so the picked file is read or written right away. */
class DesktopBackupFileLauncher : BackupFileLauncher {

    @Composable
    override fun rememberSaveBackupFile(
        bytes: ByteArray?,
        onSave: () -> Unit,
        onCancel: () -> Unit,
        onError: (Throwable) -> Unit,
    ): (fileName: String) -> Unit {
        val currentBytes by rememberUpdatedState(bytes)
        return { fileName ->
            val file = chooseFile(FileDialog.SAVE, fileName)
            val data = currentBytes
            if (file == null || data == null) {
                onCancel()
            } else {
                try {
                    writeFileAtomically(file.toPath(), data)
                    onSave()
                } catch (e: IOException) {
                    onError(e)
                }
            }
        }
    }

    @Composable
    override fun rememberOpenBackupFile(onOpen: (source: BackupFileSource, fileName: String?) -> Unit): () -> Unit = {
        chooseFile(FileDialog.LOAD, null)?.let { file -> onOpen(BackupFileSource(file::readBytes), file.name) }
    }
}

/** A modal AWT file dialog; `null` when the user cancels. */
internal fun chooseFile(mode: Int, fileName: String?): File? {
    val dialog = FileDialog(null as Frame?, null, mode).apply {
        file = fileName
        isVisible = true
    }
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}

/** Replaces [target] only once [data] is fully written, so a failed save keeps the previous backup. */
internal fun writeFileAtomically(target: Path, data: ByteArray) {
    val temp = Files.createTempFile(target.toAbsolutePath().parent, "${target.fileName}.", ".tmp")
    try {
        Files.write(temp, data)
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temp)
    }
}
