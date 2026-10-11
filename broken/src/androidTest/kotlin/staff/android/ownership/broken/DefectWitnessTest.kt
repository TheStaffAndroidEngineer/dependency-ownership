package staff.android.ownership.broken

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test

class DefectWitnessTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    @Test fun distinctOwnersLetCorrectScreensDisagree() {
        ui.onNodeWithText("Add 1 Tea from Saved").performScrollTo().performClick()
        ui.onNodeWithText("Lab controls").performScrollTo().performClick()
        ui.onNodeWithText("Cart Tea: 3").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Saved sees: 4").performScrollTo().assertIsDisplayed()
    }
}
