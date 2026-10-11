package staff.android.ownership.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import staff.android.ownership.session.CartSession
import staff.android.ownership.session.SessionEdit
import staff.android.ownership.session.SessionOwner

open class ShopModel(
    private val owner: SessionOwner,
    private val savedOwner: SessionOwner = owner,
) : ViewModel() {
    val cart = owner.current
    val saved = savedOwner.current
    private val heldA = owner.current.value
    private val message = MutableStateFlow("No edit yet.")
    val result = message.asStateFlow()

    fun addFromSaved(session: CartSession) {
        viewModelScope.launch {
            message.value = describe("Saved", session.add("tea", 1))
        }
    }

    fun switchAccount(account: String) {
        viewModelScope.launch {
            owner.switchTo(account)
            if (savedOwner !== owner) savedOwner.switchTo(account)
            message.value =
                "Selected account $account."
        }
    }

    fun replayHeldA() {
        viewModelScope.launch {
            message.value = describe("Held A", heldA.add("tea", 1))
        }
    }
}

private fun describe(source: String, edit: SessionEdit): String =
    when (edit) {
        is SessionEdit.Applied ->
            "$source applied: ${edit.snapshot.quantityOf("tea")}"
        is SessionEdit.Rejected -> "$source rejected: ${edit.reason}"
        is SessionEdit.Expired -> "$source expired; no cart changed."
    }
