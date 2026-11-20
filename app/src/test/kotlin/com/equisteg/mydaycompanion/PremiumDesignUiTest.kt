package com.equisteg.mydaycompanion

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.equisteg.mydaycompanion.ui.screens.PlannerCommandCenter
import com.equisteg.mydaycompanion.ui.screens.SettingsPremiumHero
import com.equisteg.mydaycompanion.ui.screens.MemoryPremiumHero
import com.equisteg.mydaycompanion.ui.screens.HistoryPremiumHero
import com.equisteg.mydaycompanion.ui.screens.RoutinePremiumHero
import com.equisteg.mydaycompanion.ui.theme.MyDayCompanionTheme
import androidx.compose.foundation.layout.Column
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class PremiumDesignUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `all supporting sections have their own premium title`() {
        compose.setContent { MyDayCompanionTheme {
            Column {
                RoutinePremiumHero(active = 2, total = 5)
                MemoryPremiumHero(remembered = 3, waiting = 1)
                HistoryPremiumHero(count = 7)
                SettingsPremiumHero()
            }
        } }
        compose.onNodeWithTag("premium-header-routines").assertExists()
        compose.onNodeWithTag("premium-header-memory").assertExists()
        compose.onNodeWithTag("premium-header-history").assertExists()
        compose.onNodeWithTag("premium-header-settings").assertExists()
    }

    @Test fun `planner command center actions are fully wired`() {
        var weeks = 0
        var searches = 0
        var replans = 0
        compose.setContent { MyDayCompanionTheme {
            PlannerCommandCenter(
                onWeek = { weeks++ },
                onFind = { searches++ },
                onReplan = { replans++ },
                busy = false,
            )
        } }
        compose.onNodeWithTag("premium-open-week").performClick()
        compose.onNodeWithTag("premium-open-search").performClick()
        compose.onNodeWithTag("premium-replan").performClick()
        assertEquals(1, weeks)
        assertEquals(1, searches)
        assertEquals(1, replans)
    }
}
