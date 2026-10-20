package cash.p.terminal.modules.pin.core

import cash.p.terminal.wallet.entities.SecretString

class PinDbStorage(private val pinDao: PinDao) {

    fun isLastLevelPinSet(): Boolean {
        val lastLevelPin = pinDao.getLastLevelPin()
        return lastLevelPin?.passcode != null
    }

    fun store(passcode: String, level: Int) {
        val pin = Pin(level, SecretString(passcode))
        pinDao.insert(pin)
    }

    fun clearPasscode(level: Int) {
        val pin = Pin(level, null)
        pinDao.insert(pin)
    }

    fun getLevel(passcode: String): Int? {
        return pinDao.getAll().find {
            it.passcode?.value == passcode
        }?.level
    }

    fun getPinLevelLast(): Int {
        return pinDao.getLastLevelPin()?.level ?: 0
    }

    fun deleteAllFromLevel(level: Int) {
        pinDao.deleteAllFromLevel(level)
    }

    fun deleteForLevel(level: Int) {
        pinDao.deleteForLevel(level)
    }

    fun deleteUserLevelsFromLevel(level: Int) {
        pinDao.deleteUserLevelsFromLevel(level)
    }

    fun deleteLogLoggingPinsFromLevel(userLevel: Int) {
        pinDao.deleteLogLoggingPinsFromLevel(PinLevels.logLoggingLevelFor(userLevel))
    }

    fun isPinSetForLevel(level: Int): Boolean {
        return pinDao.get(level)?.passcode != null
    }

    fun getNextHiddenWalletLevel(): Int {
        val minLevel = pinDao.getMinLevel() ?: 0
        return if (minLevel < 0) minLevel - 1 else -1
    }

    fun getAllLevels(): List<Int> {
        return pinDao.getAll()
            .filter { it.passcode != null && PinLevels.isUserLevel(it.level) }
            .map { it.level }
    }
}
