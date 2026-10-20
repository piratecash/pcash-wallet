package cash.p.terminal.modules.manageaccount.showextendedkey

import io.horizontalsystems.core.common.CommonParcelable
import io.horizontalsystems.core.common.CommonParcelize

object ShowExtendedKeyModule {
    @CommonParcelize
    sealed class DisplayKeyType : CommonParcelable {
        @CommonParcelize
        object Bip32RootKey : DisplayKeyType()

        @CommonParcelize
        class AccountPrivateKey(val derivable: Boolean) : DisplayKeyType()

        @CommonParcelize
        class AccountPublicKey(val derivable: Boolean) : DisplayKeyType()

        val isDerivable: Boolean
            get() = when (this) {
                is AccountPrivateKey -> derivable
                is AccountPublicKey -> derivable
                Bip32RootKey -> false
            }

        val isPrivate: Boolean
            get() = when (this) {
                is AccountPrivateKey -> true
                is AccountPublicKey -> false
                Bip32RootKey -> true
            }
    }
}
