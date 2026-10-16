package cash.p.terminal.shared.main

import cash.p.terminal.entities.LaunchPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MainDestinationTest {

    @Test
    fun entries_returnsNavigationOrder() {
        assertEquals(
            listOf(
                MainDestination.Balance,
                MainDestination.Transactions,
                MainDestination.Market,
                MainDestination.Settings,
            ),
            MainDestination.entries.toList(),
        )
    }

    @Test
    fun fromString_validAndUnknownValues_returnsDestinationOrNull() {
        assertEquals(MainDestination.Market, MainDestination.fromString("Market"))
        assertNull(MainDestination.fromString("Unknown"))
        assertNull(MainDestination.fromString(null))
    }

    @Test
    fun launchDestination_marketsEnabled_followsTheLaunchPage() {
        val expected = mapOf(
            LaunchPage.Market to MainDestination.Market,
            LaunchPage.Watchlist to MainDestination.Market,
            LaunchPage.Balance to MainDestination.Balance,
            LaunchPage.Auto to MainDestination.Transactions,
        )

        LaunchPage.entries.forEach { page ->
            val destination = launchDestination(page, true, MainDestination.Transactions)
            assertEquals(expected.getValue(page), destination, page.name)
        }
    }

    @Test
    fun launchDestination_marketsDisabled_alwaysOpensBalance() {
        LaunchPage.entries.forEach { page ->
            val destination = launchDestination(page, false, MainDestination.Transactions)
            assertEquals(MainDestination.Balance, destination, page.name)
        }
    }

    @Test
    fun rememberedMainTab_settings_isNotRemembered() {
        assertNull(rememberedMainTab(MainDestination.Settings))
        assertEquals(MainDestination.Market, rememberedMainTab(MainDestination.Market))
    }
}
