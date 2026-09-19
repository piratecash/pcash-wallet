package cash.p.terminal.modules.send.beam

import cash.p.terminal.core.adapters.BeamAdapter
import cash.p.terminal.core.managers.BeamNetwork
import cash.p.terminal.core.managers.BeamSessionOwner
import cash.p.terminal.modules.send.SendConfirmationFragment.Type
import cash.p.terminal.modules.send.SendFragment.ProceedActionData
import cash.p.terminal.wallet.AccountType
import cash.p.terminal.wallet.Wallet
import cash.p.terminal.wallet.entities.TokenType
import io.horizontalsystems.core.entities.BlockchainType

internal fun isNativeBeamSendWallet(
    wallet: Wallet,
    session: BeamSessionOwner.Session?,
    adapter: BeamAdapter?,
): Boolean = wallet.token.blockchainType == BlockchainType.Beam &&
    wallet.coin.uid == "beam" && wallet.token.type == TokenType.Native &&
    wallet.token.decimals == 8 && wallet.account.type is AccountType.Mnemonic &&
    session != null && session.accountId == wallet.account.id &&
    session.network == BeamNetwork.Mainnet && adapter?.accountId == wallet.account.id

// Check before accessing any generic address checker, including for malformed legacy inputs.
internal fun handleBeamProceed(data: ProceedActionData, confirm: () -> Unit): Boolean {
    if (data.type != Type.Beam && data.wallet.token.blockchainType != BlockchainType.Beam) return false
    confirm()
    return true
}
