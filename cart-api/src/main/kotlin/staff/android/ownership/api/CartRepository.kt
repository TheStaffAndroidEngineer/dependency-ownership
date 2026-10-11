package staff.android.ownership.api

import java.util.Collections
import kotlinx.coroutines.flow.StateFlow

data class CartItem(val productId: String, val quantity: Int) {
    init {
        require(productId.isNotBlank())
        require(quantity > 0)
    }
}

class CartSnapshot(lines: List<CartItem>, val revision: Long) {
    val lines: List<CartItem> =
        Collections.unmodifiableList(ArrayList(lines))

    init {
        require(revision >= 0)
        require(
            this.lines.map { it.productId }.distinct().size ==
                this.lines.size
        )
    }

    fun quantityOf(productId: String): Int =
        lines.firstOrNull { it.productId == productId }?.quantity ?: 0

    override fun equals(other: Any?): Boolean =
        other is CartSnapshot &&
            lines == other.lines &&
            revision == other.revision

    override fun hashCode(): Int =
        31 * lines.hashCode() + revision.hashCode()

    override fun toString(): String =
        "CartSnapshot(lines=$lines, revision=$revision)"
}

enum class CartRejection {
    UNKNOWN_PRODUCT,
    INVALID_QUANTITY,
    MAXIMUM_EXCEEDED,
}

sealed interface CartEdit {
    val snapshot: CartSnapshot

    data class Accepted(override val snapshot: CartSnapshot) : CartEdit

    data class Rejected(
        override val snapshot: CartSnapshot,
        val reason: CartRejection,
    ) : CartEdit
}

interface CartRepository {
    val state: StateFlow<CartSnapshot>

    suspend fun add(productId: String, amount: Int): CartEdit

    suspend fun setQuantity(productId: String, quantity: Int): CartEdit
}
