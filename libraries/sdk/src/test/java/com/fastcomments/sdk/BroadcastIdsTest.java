package com.fastcomments.sdk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fastcomments.core.CommentWidgetConfig;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class BroadcastIdsTest {

    private static void assertKeepsOnlyRecentIds(Set<String> ids) {
        for (int i = 0; i < 1000; i++) {
            ids.add("id-" + i);
        }
        assertTrue("size was " + ids.size(), ids.size() <= 200);
        assertTrue(ids.contains("id-999"));
        assertFalse(ids.contains("id-0"));
    }

    @Test
    public void commentsSdkKeepsOnlyRecentIds() {
        assertKeepsOnlyRecentIds(new FastCommentsSDK(new CommentWidgetConfig("tenant", "url"), true).broadcastIdsSent);
    }

    @Test
    public void feedSdkKeepsOnlyRecentIds() {
        assertKeepsOnlyRecentIds(new FastCommentsFeedSDK(new CommentWidgetConfig("tenant", "url")).broadcastIdsSent);
    }
}
