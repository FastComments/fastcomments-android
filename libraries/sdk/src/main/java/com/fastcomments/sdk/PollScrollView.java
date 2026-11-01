package com.fastcomments.sdk;

import android.content.Context;
import android.graphics.Insets;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.ScrollView;

/**
 * Wraps its content until it reaches {@link #setMaxHeight(int)}, then scrolls. Used by the voter list and the poll
 * builder, which must leave room for the keyboard and the input row.
 */
public class PollScrollView extends ScrollView {

    private int maxHeight = 0;
    private float windowFraction = 0;
    private final ViewTreeObserver.OnGlobalLayoutListener windowLayoutListener = this::onWindowLayout;

    public PollScrollView(Context context) {
        super(context);
    }

    public PollScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setMaxHeight(int maxHeight) {
        if (this.maxHeight == maxHeight) {
            return;
        }
        this.maxHeight = maxHeight;
        requestLayout();
    }

    /**
     * Caps the height at a fraction of the window space left by the system bars and keyboard, re-measured on every
     * layout so rotation, split screen and the keyboard all resize it.
     */
    public void setMaxHeightFraction(float fraction) {
        windowFraction = fraction;
        // Covers the first layout, before the window has been measured.
        setMaxHeight(Math.round(getResources().getDisplayMetrics().heightPixels * fraction));
        onWindowLayout();
    }

    void onWindowLayout() {
        if (windowFraction <= 0) {
            return;
        }
        int available = getAvailableWindowHeight(getRootView());
        if (available > 0) {
            setMaxHeight(Math.round(available * windowFraction));
        }
    }

    private static int getAvailableWindowHeight(View root) {
        int height = root.getHeight();
        WindowInsets insets = root.getRootWindowInsets();
        if (insets == null) {
            return height;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            return height - bars.top - bars.bottom;
        }
        // Before R the system window insets include the keyboard.
        return height - insets.getSystemWindowInsetTop() - insets.getSystemWindowInsetBottom();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(windowLayoutListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnGlobalLayoutListener(windowLayoutListener);
        super.onDetachedFromWindow();
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
