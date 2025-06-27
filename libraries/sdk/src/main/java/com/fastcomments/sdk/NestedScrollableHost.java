package com.fastcomments.sdk;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

/**
 * Layout that prevents horizontal touch event conflicts when nesting a horizontal ViewPager2
 * inside another horizontal ViewPager2. This specifically handles the case where image galleries
 * need to work properly when the feed is embedded in a parent ViewPager2.
 * 
 * Key behaviors:
 * - Only handles horizontal scroll conflicts (preserves vertical scrolling)
 * - Allows child ViewPager2 to consume horizontal swipes when it can scroll
 * - Delegates to parent when child reaches horizontal bounds
 * - Never interferes with vertical scrolling of the feed
 * 
 * Based on Google's NestedScrollableHost but optimized for horizontal-only conflicts.
 */
public class NestedScrollableHost extends FrameLayout {
    private int touchSlop;
    private float initialX = 0f;
    private float initialY = 0f;

    public NestedScrollableHost(@NonNull Context context) {
        super(context);
        init();
    }

    public NestedScrollableHost(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NestedScrollableHost(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        handleInterceptTouchEvent(ev);
        return super.onInterceptTouchEvent(ev);
    }

    private void handleInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialX = ev.getX();
                initialY = ev.getY();
                // Don't block parent initially - let them know we might want to handle horizontals
                break;

            case MotionEvent.ACTION_MOVE:
                float deltaX = Math.abs(ev.getX() - initialX);
                float deltaY = Math.abs(ev.getY() - initialY);
                
                // Only handle horizontal scroll conflicts when parent is horizontal ViewPager2
                if (isParentHorizontalViewPager() && deltaX > touchSlop && deltaX > deltaY) {
                    // This is primarily a horizontal swipe
                    boolean swipeLeft = (ev.getX() - initialX) < 0;
                    
                    // Check if our child ViewPager2 can scroll in the swipe direction
                    if (canChildScrollHorizontally(swipeLeft)) {
                        // Child can handle this horizontal scroll, block parent
                        getParent().requestDisallowInterceptTouchEvent(true);
                    } else {
                        // Child can't scroll further horizontally, let parent handle
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                } else if (deltaY > touchSlop && deltaY > deltaX) {
                    // This is primarily a vertical swipe - always let parent handle
                    // (for feed scrolling)
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                break;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // Reset state
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }
    }

    /**
     * Check if the child view can scroll horizontally in the specified direction
     * @param swipeLeft true if swiping left, false if swiping right
     * @return true if the child can scroll in the specified direction
     */
    private boolean canChildScrollHorizontally(boolean swipeLeft) {
        View child = getChildAt(0);
        if (child == null) {
            return false;
        }

        if (swipeLeft) {
            // Swiping left means scrolling to show next item (positive direction)
            return child.canScrollHorizontally(1);
        } else {
            // Swiping right means scrolling to show previous item (negative direction)
            return child.canScrollHorizontally(-1);
        }
    }

    /**
     * Check if there's a parent ViewPager2 that's oriented horizontally
     * We only need to handle conflicts with horizontal parent ViewPagers
     */
    private boolean isParentHorizontalViewPager() {
        ViewPager2 parentVp = findParentViewPager2();
        if (parentVp != null) {
            return parentVp.getOrientation() == ViewPager2.ORIENTATION_HORIZONTAL;
        }
        // If no parent ViewPager2, no conflict to handle
        return false;
    }

    /**
     * Find the parent ViewPager2 in the view hierarchy
     */
    private ViewPager2 findParentViewPager2() {
        View parent = (View) getParent();
        while (parent != null) {
            if (parent instanceof ViewPager2) {
                return (ViewPager2) parent;
            }
            if (parent.getParent() instanceof View) {
                parent = (View) parent.getParent();
            } else {
                break;
            }
        }
        return null;
    }
}