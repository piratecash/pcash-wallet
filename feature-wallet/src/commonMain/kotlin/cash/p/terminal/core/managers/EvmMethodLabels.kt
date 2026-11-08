package cash.p.terminal.core.managers

import cash.p.terminal.resources.Res
import cash.p.terminal.resources.evm_method_accept_offer
import cash.p.terminal.resources.evm_method_add_liquidity
import cash.p.terminal.resources.evm_method_approve
import cash.p.terminal.resources.evm_method_batch
import cash.p.terminal.resources.evm_method_bid
import cash.p.terminal.resources.evm_method_borrow
import cash.p.terminal.resources.evm_method_build
import cash.p.terminal.resources.evm_method_burn
import cash.p.terminal.resources.evm_method_buy
import cash.p.terminal.resources.evm_method_cancel_auction
import cash.p.terminal.resources.evm_method_cancel_nft_listing
import cash.p.terminal.resources.evm_method_cancel_order
import cash.p.terminal.resources.evm_method_claim
import cash.p.terminal.resources.evm_method_claim_reward
import cash.p.terminal.resources.evm_method_claim_rewards
import cash.p.terminal.resources.evm_method_close_position
import cash.p.terminal.resources.evm_method_collect
import cash.p.terminal.resources.evm_method_contribute
import cash.p.terminal.resources.evm_method_deposit
import cash.p.terminal.resources.evm_method_exchange
import cash.p.terminal.resources.evm_method_flash_loan
import cash.p.terminal.resources.evm_method_get_reward
import cash.p.terminal.resources.evm_method_harvest
import cash.p.terminal.resources.evm_method_liquidate
import cash.p.terminal.resources.evm_method_liquidate_position
import cash.p.terminal.resources.evm_method_lock
import cash.p.terminal.resources.evm_method_mint
import cash.p.terminal.resources.evm_method_multicall
import cash.p.terminal.resources.evm_method_offer
import cash.p.terminal.resources.evm_method_open
import cash.p.terminal.resources.evm_method_place_bid
import cash.p.terminal.resources.evm_method_process_rollup
import cash.p.terminal.resources.evm_method_purchase
import cash.p.terminal.resources.evm_method_redeem
import cash.p.terminal.resources.evm_method_redeem_underlying
import cash.p.terminal.resources.evm_method_register
import cash.p.terminal.resources.evm_method_relay_tokens
import cash.p.terminal.resources.evm_method_remove_liquidity
import cash.p.terminal.resources.evm_method_renew
import cash.p.terminal.resources.evm_method_repay
import cash.p.terminal.resources.evm_method_repay_borrow
import cash.p.terminal.resources.evm_method_sale
import cash.p.terminal.resources.evm_method_send
import cash.p.terminal.resources.evm_method_set_sale_price
import cash.p.terminal.resources.evm_method_set_user_use_reserve_as_collateral
import cash.p.terminal.resources.evm_method_stake
import cash.p.terminal.resources.evm_method_swap
import cash.p.terminal.resources.evm_method_transfer
import cash.p.terminal.resources.evm_method_unlock
import cash.p.terminal.resources.evm_method_unstake
import cash.p.terminal.resources.evm_method_unwrap
import cash.p.terminal.resources.evm_method_update_state
import cash.p.terminal.resources.evm_method_verify_blocks
import cash.p.terminal.resources.evm_method_vote
import cash.p.terminal.resources.evm_method_withdraw
import cash.p.terminal.resources.evm_method_wrap
import io.horizontalsystems.core.Translator
import org.jetbrains.compose.resources.StringResource
import java.util.Locale

fun localizedEvmMethodLabel(label: String): String =
    evmMethodLabelResource(label)?.let { Translator.getString(it) } ?: label

internal fun evmMethodLabelResource(label: String): StringResource? =
    labelResources[label.trim().lowercase(Locale.ROOT)]

private val labelResources: Map<String, StringResource> = mapOf(
    "deposit" to Res.string.evm_method_deposit,
    "mint" to Res.string.evm_method_mint,
    "claim" to Res.string.evm_method_claim,
    "withdraw" to Res.string.evm_method_withdraw,
    "remove liquidity" to Res.string.evm_method_remove_liquidity,
    "add liquidity" to Res.string.evm_method_add_liquidity,
    "unstake" to Res.string.evm_method_unstake,
    "stake" to Res.string.evm_method_stake,
    "swap" to Res.string.evm_method_swap,
    "vote" to Res.string.evm_method_vote,
    "redeem" to Res.string.evm_method_redeem,
    "claim rewards" to Res.string.evm_method_claim_rewards,
    "borrow" to Res.string.evm_method_borrow,
    "purchase" to Res.string.evm_method_purchase,
    "repay" to Res.string.evm_method_repay,
    "cancel order" to Res.string.evm_method_cancel_order,
    "bid" to Res.string.evm_method_bid,
    "claim reward" to Res.string.evm_method_claim_reward,
    "send" to Res.string.evm_method_send,
    "transfer" to Res.string.evm_method_transfer,
    "accept offer" to Res.string.evm_method_accept_offer,
    "approve" to Res.string.evm_method_approve,
    "buy" to Res.string.evm_method_buy,
    "lock" to Res.string.evm_method_lock,
    "register" to Res.string.evm_method_register,
    "sale" to Res.string.evm_method_sale,
    "burn" to Res.string.evm_method_burn,
    "flash loan" to Res.string.evm_method_flash_loan,
    "batch" to Res.string.evm_method_batch,
    "build" to Res.string.evm_method_build,
    "cancel auction" to Res.string.evm_method_cancel_auction,
    "cancel nft listing" to Res.string.evm_method_cancel_nft_listing,
    "close position" to Res.string.evm_method_close_position,
    "collect" to Res.string.evm_method_collect,
    "contribute" to Res.string.evm_method_contribute,
    "exchange" to Res.string.evm_method_exchange,
    "get reward" to Res.string.evm_method_get_reward,
    "harvest" to Res.string.evm_method_harvest,
    "liquidate" to Res.string.evm_method_liquidate,
    "liquidate position" to Res.string.evm_method_liquidate_position,
    "multicall" to Res.string.evm_method_multicall,
    "offer" to Res.string.evm_method_offer,
    "open" to Res.string.evm_method_open,
    "place bid" to Res.string.evm_method_place_bid,
    "process rollup" to Res.string.evm_method_process_rollup,
    "redeem underlying" to Res.string.evm_method_redeem_underlying,
    "relay tokens" to Res.string.evm_method_relay_tokens,
    "renew" to Res.string.evm_method_renew,
    "repay borrow" to Res.string.evm_method_repay_borrow,
    "set sale price" to Res.string.evm_method_set_sale_price,
    "set user use reserve as collateral" to Res.string.evm_method_set_user_use_reserve_as_collateral,
    "unlock" to Res.string.evm_method_unlock,
    "unwrap" to Res.string.evm_method_unwrap,
    "update state" to Res.string.evm_method_update_state,
    "verify blocks" to Res.string.evm_method_verify_blocks,
    "wrap" to Res.string.evm_method_wrap,
)
