package staff.android.ownership

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import staff.android.ownership.api.CartItem
import staff.android.ownership.api.CartSnapshot
import staff.android.ownership.session.CartSession
import staff.android.ownership.session.SessionEdit
import staff.android.ownership.session.SessionKey
import staff.android.ownership.session.SessionOwner
import staff.android.ownership.ui.OwnershipScreen
import staff.android.ownership.ui.ShopModel

class LegacyCallbackLifetimeTest {
    @get:Rule val ui = createComposeRule()

    @Test fun replacingSessionDisposesOldViewAndCancelsItsWaitingCallback() {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val a = FakeSession(SessionKey("A", 1), 3) {
            entered.complete(Unit)
            try { release.await() } finally { cancelled.complete(Unit) }
        }
        val b = FakeSession(SessionKey("B", 2), 1) {}
        val owner = object : SessionOwner {
            override val current = MutableStateFlow<CartSession>(a)
            override suspend fun switchTo(accountId: String): CartSession {
                current.value = b
                return b
            }
        }
        val model = ShopModel(owner)
        ui.setContent { OwnershipScreen(model, "Callback lifetime test") }
        ui.onNodeWithText("Lab controls").performScrollTo().performClick()
        ui.onNodeWithText("Open legacy Cart").performScrollTo().performClick()
        ui.onNodeWithTag("legacy-cart").performScrollTo()
        onView(withText("Legacy add 1 Tea")).perform(click())
        ui.waitUntil { entered.isCompleted }
        ui.onNodeWithText("Switch to B").performScrollTo().performClick()
        ui.waitUntil { cancelled.isCompleted }
        assertTrue(cancelled.isCompleted)
        release.complete(Unit)
        ui.onNodeWithText("Cart Tea: 1").performScrollTo()
        ui.onNodeWithTag("legacy-cart").performScrollTo()
        onView(withText("No legacy edit yet.")).check(matches(isDisplayed()))
        ui.onNodeWithTag("legacy-cart").performScrollTo()
        onView(withText("Legacy add 1 Tea")).perform(click())
        ui.waitForIdle()
        onView(withText("Legacy applied: 2")).check(matches(isDisplayed()))
    }

    private class FakeSession(
        override val key: SessionKey,
        initial: Int,
        private val beforeCommit: suspend () -> Unit,
    ) : CartSession {
        override val state = MutableStateFlow(CartSnapshot(listOf(CartItem("tea", initial)), 0))
        override suspend fun add(productId: String, amount: Int): SessionEdit {
            beforeCommit()
            val next = CartSnapshot(listOf(CartItem("tea", state.value.quantityOf("tea") + amount)), 1)
            state.value = next
            return SessionEdit.Applied(next)
        }
        override suspend fun setQuantity(productId: String, quantity: Int): SessionEdit = error("Not used by this seam")
    }
}
