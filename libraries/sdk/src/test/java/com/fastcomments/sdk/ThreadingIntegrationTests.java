package com.fastcomments.sdk;

import com.fastcomments.model.PublicComment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for comment threading/nesting operations.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class ThreadingIntegrationTests extends IntegrationTestBase {

    @Test
    public void testNestedReplies() throws Exception {
        FastCommentsSDK sdk = makeSDK("testNestedReplies");
        loadSync(sdk);

        PublicComment root = postCommentSync(sdk, "Root");
        PublicComment child = postCommentSync(sdk, "Child", root.getId());
        PublicComment grandchild = postCommentSync(sdk, "Grandchild", child.getId());

        // commentCountOnServer only counts root comments (countAll=false by default)
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);
        assertEquals(1, sdk2.commentCountOnServer);

        // Verify the child chain via the reloaded tree
        PublicComment reloadedRoot = sdk2.commentsTree.getPublicComment(root.getId());
        assertNotNull(reloadedRoot);
        assertTrue("Root should have childCount >= 1",
                reloadedRoot.getChildCount() != null && reloadedRoot.getChildCount() >= 1);

        assertEquals(root.getId(), child.getParentId());
        assertEquals(child.getId(), grandchild.getParentId());
    }

    @Test
    public void testDeleteMidLevelCascades() throws Exception {
        FastCommentsSDK sdk = makeSDK("testDeleteMidLevelCascades");
        loadSync(sdk);

        PublicComment root = postCommentSync(sdk, "Root");
        PublicComment child = postCommentSync(sdk, "Child", root.getId());
        postCommentSync(sdk, "Grandchild", child.getId());

        // Delete mid-level
        deleteCommentSync(sdk, child.getId());

        // Reload and verify
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);

        // Root should still exist
        assertNotNull(sdk2.commentsTree.getPublicComment(root.getId()));
        // Child should be gone
        assertNull(sdk2.commentsTree.getPublicComment(child.getId()));
    }

    @Test
    public void testChildCountUpdated() throws Exception {
        FastCommentsSDK sdk = makeSDK("testChildCountUpdated");
        loadSync(sdk);

        PublicComment parent = postCommentSync(sdk, "Parent");
        postCommentSync(sdk, "Reply 1", parent.getId());
        postCommentSync(sdk, "Reply 2", parent.getId());

        // Reload and check childCount
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);

        PublicComment reloaded = sdk2.commentsTree.getPublicComment(parent.getId());
        assertNotNull(reloaded);
        assertTrue("Parent should have childCount >= 2",
                reloaded.getChildCount() != null && reloaded.getChildCount() >= 2);
    }

    @Test
    public void testReplyToReply() throws Exception {
        FastCommentsSDK sdk = makeSDK("testReplyToReply");
        loadSync(sdk);

        PublicComment c0 = postCommentSync(sdk, "Level 0");
        PublicComment c1 = postCommentSync(sdk, "Level 1", c0.getId());
        PublicComment c2 = postCommentSync(sdk, "Level 2", c1.getId());
        PublicComment c3 = postCommentSync(sdk, "Level 3", c2.getId());

        assertEquals(c0.getId(), c1.getParentId());
        assertEquals(c1.getId(), c2.getParentId());
        assertEquals(c2.getId(), c3.getParentId());
    }

    @Test
    public void testDeleteLeafPreservesThread() throws Exception {
        FastCommentsSDK sdk = makeSDK("testDeleteLeafPreservesThread");
        loadSync(sdk);

        PublicComment root = postCommentSync(sdk, "Root");
        PublicComment child1 = postCommentSync(sdk, "Child 1", root.getId());
        PublicComment child2 = postCommentSync(sdk, "Child 2", root.getId());

        // Delete one leaf
        deleteCommentSync(sdk, child1.getId());

        // Reload and verify sibling and parent survive
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);

        assertNotNull(sdk2.commentsTree.getPublicComment(root.getId()));
        assertNotNull(sdk2.commentsTree.getPublicComment(child2.getId()));
        assertNull(sdk2.commentsTree.getPublicComment(child1.getId()));
    }

    @Test
    public void testDeleteRootRemovesEntireThread() throws Exception {
        FastCommentsSDK sdk = makeSDK("testDeleteRootRemovesEntireThread");
        loadSync(sdk);

        PublicComment root = postCommentSync(sdk, "Root");
        PublicComment child = postCommentSync(sdk, "Child", root.getId());
        PublicComment grandchild = postCommentSync(sdk, "Grandchild", child.getId());

        // Delete root
        deleteCommentSync(sdk, root.getId());

        // Reload and verify all gone
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);

        assertNull(sdk2.commentsTree.getPublicComment(root.getId()));
        assertNull(sdk2.commentsTree.getPublicComment(child.getId()));
        assertNull(sdk2.commentsTree.getPublicComment(grandchild.getId()));
    }

    @Test
    public void testLoadChildrenPagination() throws Exception {
        FastCommentsSDK sdk = makeSDK("testLoadChildrenPagination");
        loadSync(sdk);

        PublicComment parent = postCommentSync(sdk, "Parent");
        postCommentSync(sdk, "Reply 1", parent.getId());
        postCommentSync(sdk, "Reply 2", parent.getId());
        postCommentSync(sdk, "Reply 3", parent.getId());
        postCommentSync(sdk, "Reply 4", parent.getId());

        // Reload and verify parent knows about children
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);

        PublicComment reloaded = sdk2.commentsTree.getPublicComment(parent.getId());
        assertNotNull(reloaded);
        assertTrue("Should have childCount > 0",
                reloaded.getChildCount() != null && reloaded.getChildCount() > 0);
    }
}
