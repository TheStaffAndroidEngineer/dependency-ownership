package staff.android.ownership.sessionimpl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import staff.android.ownership.api.CartEdit
import staff.android.ownership.api.CartItem
import staff.android.ownership.api.CartRepository
import staff.android.ownership.impl.createCart
import staff.android.ownership.session.CartSession
import staff.android.ownership.session.SessionEdit
import staff.android.ownership.session.SessionKey
import staff.android.ownership.session.SessionOwner

/**
 * Each factory call must return a fresh, independent cart. The factory
 * must not retain or expose that repository for mutation outside its
 * CartSession.
 */
fun createSessionOwner(
    initialAccount: String = "A",
    cartFactory: (String) -> CartRepository = ::seededCart,
): SessionOwner = DefaultSessionOwner(initialAccount, cartFactory)

private fun seededCart(accountId: String): CartRepository =
    createCart(
        products = setOf("tea"),
        maximumPerProduct = 4,
        initial =
            listOf(CartItem("tea", if (accountId == "A") 3 else 1)),
    )

internal class DefaultSessionOwner(
    initialAccount: String,
    private val cartFactory: (String) -> CartRepository,
) : SessionOwner {
    private val gate = Mutex()
    private var generation = 1L
    private val active =
        MutableStateFlow(newSession(initialAccount, generation))
    override val current: StateFlow<CartSession> = active.asStateFlow()

    private fun newSession(
        accountId: String,
        generation: Long,
    ): OwnedSession {
        require(accountId.isNotBlank())
        return OwnedSession(
            SessionKey(accountId, generation),
            cartFactory(accountId),
        )
    }

    override suspend fun switchTo(accountId: String): CartSession =
        gate.withLock {
            if (active.value.key.accountId == accountId)
                return@withLock active.value
            val nextGeneration = Math.addExact(generation, 1)
            // Construct before revocation: a factory failure keeps the
            // old session live.
            val replacement = newSession(accountId, nextGeneration)
            active.value.live = false
            generation = nextGeneration
            active.value = replacement
            replacement
        }

    private inner class OwnedSession(
        override val key: SessionKey,
        private val cart: CartRepository,
    ) : CartSession {
        var live = true // Read and written only while holding gate.
        override val state = cart.state

        override suspend fun add(
            productId: String,
            amount: Int,
        ): SessionEdit = gate.withLock {
            if (!live) SessionEdit.Expired(state.value)
            else cart.add(productId, amount).asSessionEdit()
        }

        override suspend fun setQuantity(
            productId: String,
            quantity: Int,
        ): SessionEdit = gate.withLock {
            if (!live) SessionEdit.Expired(state.value)
            else cart.setQuantity(productId, quantity).asSessionEdit()
        }
    }
}

private fun CartEdit.asSessionEdit(): SessionEdit =
    when (this) {
        is CartEdit.Accepted -> SessionEdit.Applied(snapshot)
        is CartEdit.Rejected -> SessionEdit.Rejected(snapshot, reason)
    }
