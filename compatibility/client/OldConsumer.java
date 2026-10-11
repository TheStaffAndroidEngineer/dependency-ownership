import staff.android.ownership.legacy.CartBadge;

public final class OldConsumer {
    public static void main(String[] args) {
        CartBadge badge = new CartBadge(4);
        if (!badge.label().equals("Tea in Cart: 4") || badge.getQuantity() != 4)
            throw new AssertionError("Old contract changed");
        System.out.println("OLD CONSUMER PASS: " + badge.label());
    }
}
