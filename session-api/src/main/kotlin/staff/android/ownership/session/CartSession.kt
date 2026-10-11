package staff.android.ownership.session

import kotlinx.coroutines.flow.StateFlow
import staff.android.ownership.api.CartRejection
import staff.android.ownership.api.CartSnapshot

data class SessionKey(val accountId: String, val generation: Long) {
    init {
        require(accountId.isNotBlank())
        require(generation > 0)
    }
}

sealed interface SessionEdit {
    val snapshot: CartSnapshot

    data class Applied(override val snapshot: CartSnapshot) :
        SessionEdit

    data class Rejected(
        override val snapshot: CartSnapshot,
        val reason: CartRejection,
    ) : SessionEdit

    data class Expired(override val snapshot: CartSnapshot) :
        SessionEdit
}

interface CartSession {
    val key: SessionKey
    val state: StateFlow<CartSnapshot>

    suspend fun add(productId: String, amount: Int): SessionEdit

    suspend fun setQuantity(
        productId: String,
        quantity: Int,
    ): SessionEdit
}

interface SessionOwner {
    val current: StateFlow<CartSession>

    suspend fun switchTo(accountId: String): CartSession
}
