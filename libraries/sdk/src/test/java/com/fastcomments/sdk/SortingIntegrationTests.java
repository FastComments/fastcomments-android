package com.fastcomments.sdk;

import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.PublicComment;
import com.fastcomments.model.SortDirections;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Integration tests for comment sorting operations.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class SortingIntegrationTests extends IntegrationTestBase {

    private FastCommentsSDK makeSDKWithSort(String urlId, SortDirections sort) {
        CommentWidgetConfig config = new CommentWidgetConfig();
        config.tenantId = getTenantId();
        config.urlId = urlId;
        config.sso = makeSSOToken();
        config.defaultSortDirection = sort;
        FastCommentsSDK sdk = new FastCommentsSDK(config);
        sdk.commentsTree.setAdapter(mock(CommentsAdapter.class));
        return sdk;
    }

    @Test
    public void testNewestFirst() throws Exception {
        FastCommentsSDK sdk = makeSDK("testNewestFirst");
        loadSync(sdk);

        postCommentSync(sdk, "Comment A");
        Thread.sleep(100); // ensure distinct timestamps
        postCommentSync(sdk, "Comment B");
        Thread.sleep(100);
        postCommentSync(sdk, "Comment C");

        // Reload with newest first
        FastCommentsSDK sdk2 = makeSDKWithSort(sdk.getConfig().urlId, SortDirections.NF);
        loadSync(sdk2);

        assertTrue("Should have comments", sdk2.commentsTree.visibleNodes.size() >= 3);
        // First visible comment should be the newest (C)
        RenderableComment first = getFirstVisibleComment(sdk2);
        assertNotNull(first);
        assertTrue("Newest first: first comment should contain 'Comment C'",
                first.getComment().getCommentHTML().contains("Comment C"));
    }

    @Test
    public void testOldestFirst() throws Exception {
        FastCommentsSDK sdk = makeSDK("testOldestFirst");
        loadSync(sdk);

        postCommentSync(sdk, "Comment A");
        Thread.sleep(100);
        postCommentSync(sdk, "Comment B");
        Thread.sleep(100);
        postCommentSync(sdk, "Comment C");

        // Reload with oldest first
        FastCommentsSDK sdk2 = makeSDKWithSort(sdk.getConfig().urlId, SortDirections.OF);
        loadSync(sdk2);

        assertTrue("Should have comments", sdk2.commentsTree.visibleNodes.size() >= 3);
        RenderableComment first = getFirstVisibleComment(sdk2);
        assertNotNull(first);
        assertTrue("Oldest first: first comment should contain 'Comment A'",
                first.getComment().getCommentHTML().contains("Comment A"));
    }

    @Test
    public void testMostRelevant() throws Exception {
        FastCommentsSDK sdk = makeSDK("testMostRelevant");
        loadSync(sdk);

        PublicComment a = postCommentSync(sdk, "Comment A - upvoted");
        postCommentSync(sdk, "Comment B - no votes");

        // Upvote comment A
        voteCommentSync(sdk, a.getId(), true);

        // Reload with most relevant
        FastCommentsSDK sdk2 = makeSDKWithSort(sdk.getConfig().urlId, SortDirections.MR);
        loadSync(sdk2);

        assertTrue("Should have comments", sdk2.commentsTree.visibleNodes.size() >= 2);
        RenderableComment first = getFirstVisibleComment(sdk2);
        assertNotNull(first);
        assertTrue("Most relevant: first comment should be the upvoted one",
                first.getComment().getCommentHTML().contains("Comment A"));
    }

    @Test
    public void testSortDirectionApplied() throws Exception {
        FastCommentsSDK sdk = makeSDK("testSortDirectionApplied");
        loadSync(sdk);

        postCommentSync(sdk, "Comment A");
        Thread.sleep(100);
        postCommentSync(sdk, "Comment B");
        Thread.sleep(100);
        postCommentSync(sdk, "Comment C");

        // Load NF
        FastCommentsSDK sdkNF = makeSDKWithSort(sdk.getConfig().urlId, SortDirections.NF);
        loadSync(sdkNF);

        // Load OF
        FastCommentsSDK sdkOF = makeSDKWithSort(sdk.getConfig().urlId, SortDirections.OF);
        loadSync(sdkOF);

        RenderableComment firstNF = getFirstVisibleComment(sdkNF);
        RenderableComment firstOF = getFirstVisibleComment(sdkOF);

        assertNotNull(firstNF);
        assertNotNull(firstOF);
        // The first comment should differ between NF and OF
        assertTrue("Different sort directions should produce different first comment",
                !firstNF.getComment().getId().equals(firstOF.getComment().getId()));
    }

    @Test
    public void testPinnedCommentStaysFirst() throws Exception {
        String urlId = makeUrlId("testPinnedCommentStaysFirst");

        // Create comments with a regular user
        FastCommentsSDK sdk = makeSDKWithUrlId(urlId);
        loadSync(sdk);
        PublicComment a = postCommentSync(sdk, "Comment A - to pin");
        Thread.sleep(100);
        postCommentSync(sdk, "Comment B");
        Thread.sleep(100);
        postCommentSync(sdk, "Comment C");

        // Pin comment A with admin
        FastCommentsSDK adminSdk = makeAdminSDKWithUrlId(urlId);
        loadSync(adminSdk);
        pinCommentSync(adminSdk, a.getId());

        // Reload with each sort direction and verify pinned comment is first
        for (SortDirections dir : new SortDirections[]{SortDirections.NF, SortDirections.OF, SortDirections.MR}) {
            FastCommentsSDK sorted = makeSDKWithSort(urlId, dir);
            loadSync(sorted);

            RenderableComment first = getFirstVisibleComment(sorted);
            assertNotNull("First comment should exist for sort " + dir, first);
            assertEquals("Pinned comment should be first for sort " + dir,
                    a.getId(), first.getComment().getId());
        }
    }

    @Test
    public void testDefaultSortDirection() throws Exception {
        FastCommentsSDK sdk = makeSDK("testDefaultSortDirection");
        // No explicit sort direction set
        loadSync(sdk);

        postCommentSync(sdk, "Comment");

        // postCommentSync does not update commentCountOnServer on the calling SDK;
        // reload on a fresh SDK to get the updated count.
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);

        assertEquals(1, sdk2.commentCountOnServer);
        assertNotNull(sdk2.commentsTree.getPublicComment(
                sdk2.commentsTree.allComments.get(0).getComment().getId()));
    }

    private RenderableComment getFirstVisibleComment(FastCommentsSDK sdk) {
        for (RenderableNode node : sdk.commentsTree.visibleNodes) {
            if (node instanceof RenderableComment) {
                return (RenderableComment) node;
            }
        }
        return null;
    }
}
