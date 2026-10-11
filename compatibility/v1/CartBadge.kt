package staff.android.ownership.legacy

class CartBadge(val quantity: Int) {
    init { require(quantity >= 0) }
    fun label(): String = "Tea in Cart: $quantity"
}
