package com.fastcomments.sdk;

import com.fastcomments.model.PublicComment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for comment Create, Read, Update, Delete operations.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CommentCRUDIntegrationTests extends IntegrationTestBase {

    @Test
    public void testLoadEmptyPage() throws Exception {
        FastCommentsSDK sdk = makeSDK("testLoadEmptyPage");
        loadSync(sdk);

        assertEquals(0, sdk.commentCountOnServer);
        assertEquals(0, sdk.commentsTree.totalSize());
        assertTrue(sdk.commentsTree.visibleNodes.isEmpty());
        assertFalse(sdk.hasMore);
        assertNull(sdk.blockingErrorMessage);
    }

    @Test
    public void testPostComment() throws Exception {
        FastCommentsSDK sdk = makeSDK("testPostComment");
        loadSync(sdk);

        PublicComment comment = postCommentSync(sdk, "Hello World");

        assertNotNull(comment);
        assertTrue(comment.getCommentHTML().contains("Hello World"));

        // Verify persisted by reloading
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);
        assertEquals(1, sdk2.commentCountOnServer);
        assertNotNull(sdk2.commentsTree.commentsById.get(comment.getId()));
    }

    @Test
    public void testPostCommentReturnsValidComment() throws Exception {
        FastCommentsSDK sdk = makeSDK("testPostCommentReturnsValidComment");
        loadSync(sdk);

        PublicComment comment = postCommentSync(sdk, "Valid comment test");

        assertNotNull(comment.getId());
        assertFalse(comment.getId().isEmpty());
        assertNotNull(comment.getCommentHTML());
        assertTrue(comment.getCommentHTML().contains("Valid comment test"));
        assertNotNull(comment.getDate());
    }

    @Test
    public void testPostReply() throws Exception {
        FastCommentsSDK sdk = makeSDK("testPostReply");
        loadSync(sdk);

        PublicComment parent = postCommentSync(sdk, "Parent comment");
        PublicComment reply = postCommentSync(sdk, "Reply comment", parent.getId());

        assertNotNull(reply);
        assertEquals(parent.getId(), reply.getParentId());

        // Verify both exist on reload
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);
        // commentCountOnServer only counts root comments by default (countAll=false)
        assertEquals(1, sdk2.commentCountOnServer);
        // But the parent should have the child
        PublicComment reloadedParent = sdk2.commentsTree.getPublicComment(parent.getId());
        assertNotNull(reloadedParent);
        assertTrue("Parent should have children",
                reloadedParent.getChildCount() != null && reloadedParent.getChildCount() >= 1);
    }

    @Test
    public void testPostEmptyCommentFails() throws Exception {
        FastCommentsSDK sdk = makeSDK("testPostEmptyCommentFails");
        loadSync(sdk);

        assertNotNull(postCommentSyncExpectFailure(sdk, ""));
    }

    @Test
    public void testEditComment() throws Exception {
        FastCommentsSDK sdk = makeSDK("testEditComment");
        loadSync(sdk);

        PublicComment comment = postCommentSync(sdk, "Original text");
        editCommentSync(sdk, comment.getId(), "Updated text");

        // Verify on fresh SDK
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);

        PublicComment reloaded = sdk2.commentsTree.getPublicComment(comment.getId());
        assertNotNull(reloaded);
        assertTrue(reloaded.getCommentHTML().contains("Updated text"));
    }

    @Test
    public void testDeleteComment() throws Exception {
        FastCommentsSDK sdk = makeSDK("testDeleteComment");
        loadSync(sdk);

        PublicComment comment = postCommentSync(sdk, "To be deleted");

        // Delete using an admin SDK (has permission to delete any comment)
        FastCommentsSDK adminSdk = makeAdminSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(adminSdk);
        deleteCommentSync(adminSdk, comment.getId());

        // Verify gone on reload
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);
        assertEquals(0, sdk2.commentCountOnServer);
    }

    @Test
    public void testDeleteCommentWithReplies() throws Exception {
        FastCommentsSDK sdk = makeSDK("testDeleteCommentWithReplies");
        loadSync(sdk);

        PublicComment parent = postCommentSync(sdk, "Parent");
        postCommentSync(sdk, "Child", parent.getId());

        deleteCommentSync(sdk, parent.getId());

        // Verify parent gone
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);
        assertNull(sdk2.commentsTree.getPublicComment(parent.getId()));
    }

    @Test
    public void testPaginationViaLoadMore() throws Exception {
        FastCommentsSDK sdk = makeSDK("testPaginationViaLoadMore");
        loadSync(sdk);

        // Create 35 comments (exceeds default pageSize of 30)
        for (int i = 0; i < 35; i++) {
            postCommentSync(sdk, "Comment " + i);
        }

        // Reload on fresh SDK to test pagination
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);

        assertTrue("Should have more to load", sdk2.hasMore);
        int firstPageSize = sdk2.commentsTree.totalSize();
        assertTrue("First page should have comments", firstPageSize > 0);

        // Load more
        loadMoreSync(sdk2);

        assertTrue("Total should increase after loadMore",
                sdk2.commentsTree.totalSize() > firstPageSize);
    }

    @Test
    public void testPostMultipleRootComments() throws Exception {
        FastCommentsSDK sdk = makeSDK("testPostMultipleRootComments");
        loadSync(sdk);

        postCommentSync(sdk, "Comment 1");
        postCommentSync(sdk, "Comment 2");
        postCommentSync(sdk, "Comment 3");

        // Verify count on reload
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);
        assertEquals(3, sdk2.commentCountOnServer);
    }
}
