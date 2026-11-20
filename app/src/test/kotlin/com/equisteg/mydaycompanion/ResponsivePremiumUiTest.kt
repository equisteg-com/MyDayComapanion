package com.equisteg.mydaycompanion

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.equisteg.mydaycompanion.domain.testing.TestGraph
import com.equisteg.mydaycompanion.navigation.AppNavigation
import com.equisteg.mydaycompanion.ui.MainViewModel
import com.equisteg.mydaycompanion.ui.screens.MonthCalendar
import com.equisteg.mydaycompanion.ui.theme.MyDayCompanionTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals

/** Checks the wide-screen rail and the narrow-screen accessible calendar alternative. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w900dp-h900dp")
class TabletPremiumNavigationUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun `tablet navigation rail keeps all five areas usable`() {
        val vm=MainViewModel(TestServices(TestGraph()),Dispatchers.Unconfined)
        compose.setContent { MyDayCompanionTheme { AppNavigation(vm) } }
        compose.onNodeWithTag("nav-ROUTINES").performClick()
        compose.onNodeWithTag("premium-header-routines").assertExists()
        compose.onNodeWithTag("nav-MEMORY").performClick()
        compose.onNodeWithTag("premium-header-memory").assertExists()
        compose.onNodeWithTag("nav-HISTORY").performClick()
        compose.onNodeWithTag("premium-header-history").assertExists()
        compose.onNodeWithTag("nav-SETTINGS").performClick()
        compose.onNodeWithTag("premium-header-settings").assertExists()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w320dp-h800dp")
class CompactPremiumCalendarUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun `small phones use individually accessible calendar date rows`() {
        val day=LocalDate.of(2026,11,21)
        var chosen:LocalDate?=null
        compose.setContent { MyDayCompanionTheme {
            MonthCalendar(YearMonth.of(2026,11),day,day,emptyList(),
                onSelectDate={chosen=it},onMonthChange={})
        } }
        compose.onNodeWithTag("month-day-2026-11-01").assertExists()
        compose.onNodeWithTag("month-day-2026-11-29").assertExists()
    }
}
