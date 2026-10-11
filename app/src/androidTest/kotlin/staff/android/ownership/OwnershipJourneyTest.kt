package staff.android.ownership

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class OwnershipJourneyTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private fun click(text: String) { ui.onNodeWithText(text).performScrollTo().performClick(); ui.waitForIdle() }
    private fun sees(text: String) { ui.onNodeWithText(text).performScrollTo().assertIsDisplayed() }

    // One ordered journey per fresh instrumentation process: the application owner is intentionally shared.
    @Test fun acceptedStateRecreationAndRevokedCapabilities() {
        click("Lab controls")
        click("Add 1 Tea from Saved")
        sees("Cart Tea: 4"); sees("Saved sees: 4")
        click("Add 1 Tea from Saved")
        sees("Saved rejected: MAXIMUM_EXCEEDED"); sees("Cart Tea: 4")
        ui.activityRule.scenario.recreate()
        sees("Cart Tea: 4"); sees("Saved sees: 4")
        click("Switch to A")
        sees("Account A; generation 1"); sees("Cart Tea: 4")
        click("Switch to B"); click("Try held A handle")
        sees("Held A expired; no cart changed."); sees("Cart Tea: 1")
        click("Switch to A")
        sees("Account A; generation 3"); sees("Cart Tea: 3")
        click("Try held A handle"); sees("Held A expired; no cart changed.")
    }
}
