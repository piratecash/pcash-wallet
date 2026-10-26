package cash.p.terminal.modules.contacts

import android.content.Context
import java.io.File

class AndroidContactsFileStorage(context: Context) : ContactsFileStorage {
    private val file = File(context.filesDir, "UW_Contacts.json")

    override fun read(): String? = file.takeIf { it.exists() }?.readText()

    override fun write(json: String) = file.writeText(json)
}
