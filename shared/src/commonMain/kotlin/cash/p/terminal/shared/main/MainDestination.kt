package cash.p.terminal.shared.main

import cash.p.terminal.entities.LaunchPage

enum class MainDestination {
    Balance,
    Transactions,
    Market,
    Settings;

    companion object {
        fun fromString(value: String?): MainDestination? = entries.firstOrNull { it.name == value }
    }
}

interface MainTabStorage {
    var mainTab: MainDestination?
}

fun launchDestination(
    launchPage: LaunchPage,
    marketsTabEnabled: Boolean,
    lastTab: MainDestination,
): MainDestination = when {
    !marketsTabEnabled -> MainDestination.Balance
    launchPage == LaunchPage.Market || launchPage == LaunchPage.Watchlist -> MainDestination.Market
    launchPage == LaunchPage.Balance -> MainDestination.Balance
    else -> lastTab
}

/** Settings is never remembered: the next launch opens the last content tab. */
fun rememberedMainTab(selected: MainDestination): MainDestination? =
    selected.takeUnless { it == MainDestination.Settings }
