package cash.p.terminal.ui_compose.components

// SHORT, MEDIUM and INDEFINITE are Material Snackbar.LENGTH_SHORT / LENGTH_LONG / LENGTH_INDEFINITE.
enum class SnackbarDuration(val value: Int) {
    SHORT(-1),
    MEDIUM(0),
    LONG(7_000),
    INDEFINITE(-2),
}
