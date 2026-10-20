package cash.p.terminal.modules.restoreaccount.restoreprivatekey

object RestorePrivateKeyModule {
    open class RestoreError : Exception() {
        object EmptyText : RestoreError()
        object NotSupportedDerivedType : RestoreError()
        object NonPrivateKey : RestoreError()
        object NoValidKey : RestoreError()
    }

}
