package staff.android.ownership.impl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import staff.android.ownership.api.CartEdit
import staff.android.ownership.api.CartItem
import staff.android.ownership.api.CartRejection
import staff.android.ownership.api.CartRepository
import staff.android.ownership.api.CartSnapshot

fun createCart(
    products: Set<String>,
    maximumPerProduct: Int,
    initial: List<CartItem>,
): CartRepository =
    DefaultCart(CartRules(products, maximumPerProduct), initial)

internal class DefaultCart(
    private val rules: CartRules,
    initial: List<CartItem>,
) : CartRepository {
    private val mutex = Mutex()
    private val current =
        MutableStateFlow(
            CartSnapshot(initial.sortedBy { it.productId }, 0)
        )
    override val state = current.asStateFlow()

    init {
        require(
            current.value.lines.all {
                rules.rejection(it.productId, it.quantity.toLong()) ==
                    null
            }
        )
    }

    override suspend fun add(productId: String, amount: Int): CartEdit =
        mutex.withLock {
            if (amount <= 0) {
                CartEdit.Rejected(
                    current.value,
                    CartRejection.INVALID_QUANTITY,
                )
            } else {
                val desired =
                    current.value.quantityOf(productId).toLong() +
                        amount.toLong()
                commit(productId, desired)
            }
        }

    override suspend fun setQuantity(
        productId: String,
        quantity: Int,
    ): CartEdit = mutex.withLock {
        commit(productId, quantity.toLong())
    }

    private fun commit(productId: String, desired: Long): CartEdit {
        val before = current.value
        val rejection = rules.rejection(productId, desired)
        if (rejection != null)
            return CartEdit.Rejected(before, rejection)
        if (before.quantityOf(productId) == desired.toInt())
            return CartEdit.Accepted(before)
        val next =
            before.lines
                .filterNot { it.productId == productId }
                .toMutableList()
        if (desired > 0) next += CartItem(productId, desired.toInt())
        val after =
            CartSnapshot(
                next.sortedBy { it.productId },
                Math.addExact(before.revision, 1),
            )
        current.value = after
        return CartEdit.Accepted(after)
    }
}
