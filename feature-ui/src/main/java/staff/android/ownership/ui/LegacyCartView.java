package staff.android.ownership.ui;

import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import staff.android.ownership.legacy.CancelRequest;
import staff.android.ownership.legacy.CartBadge;
import staff.android.ownership.legacy.CartCallback;
import staff.android.ownership.legacy.CartClient;
import staff.android.ownership.session.SessionEdit;

public final class LegacyCartView extends LinearLayout {
    private final TextView quantity;
    private final TextView result;
    private final Button add;
    private CancelRequest pending;

    public LegacyCartView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        quantity = new TextView(context);
        result = new TextView(context);
        add = new Button(context);
        add.setText("Legacy add 1 Tea");
        result.setText("No legacy edit yet.");
        LegacyCartStyle.apply(context, quantity, result, add);
        addView(quantity);
        addView(add);
        addView(result);
    }

    public void render(CartClient client, int amount) {
        quantity.setText(new CartBadge(amount).label());
        add.setOnClickListener(view -> {
            if (pending != null) pending.cancel();
            pending = client.add("tea", 1, new CartCallback() {
                @Override public void completed(SessionEdit edit) {
                    if (edit instanceof SessionEdit.Applied) {
                        result.setText(
                            "Legacy applied: " +
                                edit.getSnapshot().quantityOf("tea"));
                    } else if (edit instanceof SessionEdit.Expired) {
                        result.setText("Legacy expired; no cart changed.");
                    } else {
                        result.setText("Legacy rejected; no cart changed.");
                    }
                }
                @Override public void failed(Exception error) {
                    result.setText("Legacy operation failed.");
                }
            });
        });
    }

    public void release() {
        if (pending != null) pending.cancel();
        add.setOnClickListener(null);
    }
}
