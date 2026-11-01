package com.fastcomments.sdk;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ScrollView;

/**
 * Wraps its content until it reaches {@link #setMaxHeight(int)}, then scrolls. Used by the voter list and the poll
 * builder, which must leave room for the keyboard and the input row.
 */
public class PollScrollView extends ScrollView {

    private int maxHeight = 0;

    public PollScrollView(Context context) {
        super(context);
    }

    public PollScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxHeight > 0) {
            int available = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED
                    ? maxHeight
                    : Math.min(maxHeight, MeasureSpec.getSize(heightMeasureSpec));
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
