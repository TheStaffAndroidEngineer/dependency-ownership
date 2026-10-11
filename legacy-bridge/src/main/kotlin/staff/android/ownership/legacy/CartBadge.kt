package staff.android.ownership.legacy

class CartBadge
@JvmOverloads
constructor(
    val quantity: Int,
    val caption: String = "Tea",
) {
    init {
        require(quantity >= 0)
        require(caption.isNotBlank())
    }

    fun label(): String = "$caption in Cart: $quantity"
}
