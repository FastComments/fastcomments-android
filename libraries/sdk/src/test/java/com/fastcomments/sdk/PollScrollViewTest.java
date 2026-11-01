package com.fastcomments.sdk;

import static org.junit.Assert.assertEquals;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class PollScrollViewTest {

    private static void layout(View root, int width, int height) {
        root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
    }

    @Test
    public void maxHeightFollowsTheWindow() {
        FrameLayout root = new FrameLayout(RuntimeEnvironment.getApplication());
        PollScrollView scroll = new PollScrollView(RuntimeEnvironment.getApplication());
        View content = new View(RuntimeEnvironment.getApplication());
        // ScrollView measures its child unbounded, which only a minimum height survives.
        content.setMinimumHeight(5000);
        scroll.addView(content);
        root.addView(
                scroll,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setMaxHeightFraction(1f / 3);

        layout(root, 1080, 2400);
        scroll.onWindowLayout();
        layout(root, 1080, 2400);
        assertEquals(800, scroll.getHeight());

        // Rotation, split screen, or the keyboard shrink the space the builder may take.
        layout(root, 2400, 1080);
        scroll.onWindowLayout();
        layout(root, 2400, 1080);
        assertEquals(360, scroll.getHeight());
    }
}
