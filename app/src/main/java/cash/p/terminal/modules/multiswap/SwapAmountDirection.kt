package cash.p.terminal.modules.multiswap

import androidx.annotation.Keep

/** Used as a navigation route argument: androidx.navigation resolves the enum by its original name. */
@Keep
enum class SwapAmountDirection {
    In,
    Out,
}

enum class SwapExecutionMode {
    ExactIn,
    NativeExactOut,
}

enum class SwapAmountAccuracy {
    Exact,
    AtLeast,
    Estimated,
}
