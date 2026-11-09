package cash.p.terminal.shared.desktop.vault

import cash.p.terminal.shared.desktop.DesktopWalletSettingsStorage
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.DURESS_ACCOUNT
import cash.p.terminal.shared.desktop.vault.VaultLevelsFixture.Companion.MAIN_ACCOUNT
import io.horizontalsystems.core.DesktopSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The settings file is readable without a password: nothing about levels or accounts may be written to it. */
class DesktopPlainSettingsKeysTest {
    private val fixture = VaultLevelsFixture()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settingsFile = File(fixture.dataDirectory, "settings.preferences_pb")

    @AfterTest
    fun tearDown() = runBlocking {
        scope.cancel()
        fixture.tearDown()
    }

    @Test
    fun levelAndAccountSettings_written_leaveNoKeyInTheOpenSettingsFile() = runBlocking<Unit> {
        fixture.mainWithDuress()
        val storage = DesktopWalletSettingsStorage(
            DesktopSettingsStore(settingsFile, scope),
            DesktopAccountSettings(fixture.vault),
        )

        storage.zcashAccountIds = setOf(MAIN_ACCOUNT, DURESS_ACCOUNT)
        storage.nonRecommendedAccountAlertDismissedAccounts = setOf(MAIN_ACCOUNT)
        storage.zcashIronwoodMigrationTxIds = setOf("$MAIN_ACCOUNT:hash-1")
        storage.bitcoinReplacementLinks = setOf("$DURESS_ACCOUNT|bitcoin|hash-1|uid-1|hash-2|uid-2")
        DesktopLevelSettings(fixture.vault).setFlag(0, "aml_check_received_enabled", true)
        storage.transactionHideEnabled = true

        val content = String(settingsFile.readBytes(), Charsets.ISO_8859_1)
        assertTrue("transactionHideEnabled" in content, "the control setting must land in the file")
        listOf(
            "zcashAccountIds", "nonRecommendedAccountAlertDismissedAccounts", "zcashIronwoodMigrationTxIds",
            "bitcoinReplacementLinks", "aml_check_received_enabled", MAIN_ACCOUNT, DURESS_ACCOUNT,
        ).forEach { assertFalse(it in content, "$it must not be in the open settings file") }
    }
}
