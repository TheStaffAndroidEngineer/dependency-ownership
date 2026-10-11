package staff.android.ownership.ui;

import android.content.Context;
import android.widget.Button;
import android.widget.TextView;

final class LegacyCartStyle {
    static void apply(Context context, TextView quantity, TextView result, Button add) {
        int ink = (context.getResources().getConfiguration().uiMode & 48) == 32 ? 0xffedf0f3 : 0xff192327;
        int blue = (context.getResources().getConfiguration().uiMode & 48) == 32 ? 0xffb5c4ff : 0xff3156d3;
        int onBlue = (context.getResources().getConfiguration().uiMode & 48) == 32 ? 0xff162d77 : 0xffffffff;
        for (TextView text : new TextView[]{quantity, result, add}) {
            text.setTypeface(context.getResources().getFont(R.font.cg_google_sans_regular));
            text.setTextSize(18);
            text.setTextColor(ink);
            text.setPadding(0, 12, 0, 12);
        }
        add.setTextColor(onBlue);
        add.setAllCaps(false);
        add.setMinHeight((int) (52 * context.getResources().getDisplayMetrics().density));
        add.setBackgroundTintList(android.content.res.ColorStateList.valueOf(blue));
    }
}
