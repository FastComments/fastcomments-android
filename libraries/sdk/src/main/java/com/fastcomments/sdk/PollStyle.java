package com.fastcomments.sdk;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

/**
 * Shapes shared by the poll card and the poll builder.
 */
final class PollStyle {

    static final int OPTION_RADIUS_DP = 11;
    static final int MARK_RADIUS_DP = 6;

    private PollStyle() {}

    /**
     * Rounded on every corner but the top-start one, the web chips' "border-start-start-radius: 0".
     */
    static float[] getCornerRadii(Context context, float radius) {
        boolean rtl = context.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        // Order: top-left, top-right, bottom-right, bottom-left, as x/y pairs.
        return rtl
                ? new float[] {radius, radius, 0, 0, radius, radius, radius, radius}
                : new float[] {0, 0, radius, radius, radius, radius, radius, radius};
    }

    static GradientDrawable createShape(Context context, int radiusDp, int fillColor, int strokeColor, boolean dashed) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadii(getCornerRadii(context, dp(context, radiusDp)));
        shape.setColor(fillColor);
        if (dashed) {
            shape.setStroke(dp(context, 1), strokeColor, dp(context, 4), dp(context, 3));
        } else {
            shape.setStroke(dp(context, 1), strokeColor);
        }
        return shape;
    }

    /**
     * The web footers' "gap: 6px 10px" (phone widths) as item margins. FlexboxLayout's middle dividers miscount
     * GONE children and push the last item past the edge, so they are not used here. The footer's own top margin
     * is 6dp less than the web's 12px to make up for each item's top margin.
     */
    static void applyFooterGaps(ViewGroup footer) {
        Context context = footer.getContext();
        for (int i = 0; i < footer.getChildCount(); i++) {
            View child = footer.getChildAt(i);
            ViewGroup.LayoutParams params = child.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
                margins.topMargin = dp(context, 6);
                margins.setMarginEnd(dp(context, 10));
                child.setLayoutParams(margins);
            }
        }
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
