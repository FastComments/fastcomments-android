package com.fastcomments.sdk;

import com.fastcomments.model.PublicComment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * Integration tests for user presence tracking.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PresenceIntegrationTests extends IntegrationTestBase {

    @Test
    public void testClearAllPresence() throws Exception {
        FastCommentsSDK sdk = makeSDK("testClearAllPresence");
        loadSync(sdk);

        postCommentSync(sdk, "Comment 1");
        postCommentSync(sdk, "Comment 2");

        // Manually set some presence
        for (RenderableComment rc : sdk.commentsTree.allComments) {
            rc.isOnline = true;
        }

        // Reset presence
        sdk.commentsTree.resetPresence();

        // Verify all offline
        for (RenderableComment rc : sdk.commentsTree.allComments) {
            assertFalse("All comments should be offline after resetPresence", rc.isOnline);
        }
    }

    @Test
    public void testPresenceIndexingAndPropagation() throws Exception {
        FastCommentsSDK sdk = makeSDK("testPresenceIndexingAndPropagation");
        loadSync(sdk);

        postCommentSync(sdk, "Comment 1");
        postCommentSync(sdk, "Comment 2");

        // Reload to get proper user IDs in comments
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);

        // commentsByUserId should be populated
        assertNotNull("commentsByUserId should exist", sdk2.commentsTree.commentsByUserId);

        // Find a userId from the loaded comments
        if (!sdk2.commentsTree.allComments.isEmpty()) {
            RenderableComment rc = sdk2.commentsTree.allComments.get(0);
            String userId = rc.getComment().getUserId();
            if (userId != null) {
                // Set online
                sdk2.commentsTree.updateUserPresence(userId, true);
                // Set offline
                sdk2.commentsTree.updateUserPresence(userId, false);
                // After offline, check
                assertFalse("Comment should be offline after setting presence to false", rc.isOnline);
            }
        }
    }
}
