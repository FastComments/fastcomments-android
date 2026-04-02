package com.fastcomments.sdk;

import com.fastcomments.model.FeedPost;

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
 * Integration tests for Feed SDK operations.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class FeedIntegrationTests extends IntegrationTestBase {

    @Test
    public void testLoadFeed() throws Exception {
        FastCommentsFeedSDK sdk = makeFeedSDK("testLoadFeed");
        loadFeedSync(sdk);

        assertNull("Should have no blocking error", sdk.blockingErrorMessage);
    }

    @Test
    public void testCreateFeedPost() throws Exception {
        FastCommentsFeedSDK sdk = makeFeedSDK("testCreateFeedPost");
        loadFeedSync(sdk);

        FeedPost post = createFeedPostSync(sdk, "Test Post Title", "<p>Post content</p>");

        assertNotNull(post);
        assertNotNull(post.getId());
        assertFalse(post.getId().isEmpty());
    }

    @Test
    public void testDeleteFeedPost() throws Exception {
        FastCommentsFeedSDK sdk = makeFeedSDK("testDeleteFeedPost");
        loadFeedSync(sdk);

        FeedPost post = createFeedPostSync(sdk, "To Delete", "<p>Delete me</p>");
        assertNotNull(post);

        deleteFeedPostSync(sdk, post.getId());

        // Verify removed from local list
        boolean found = false;
        for (FeedPost fp : sdk.getFeedPosts()) {
            if (post.getId().equals(fp.getId())) {
                found = true;
                break;
            }
        }
        assertFalse("Deleted post should not be in feed", found);
    }

    @Test
    public void testLikePost() throws Exception {
        FastCommentsFeedSDK sdk = makeFeedSDK("testLikePost");
        loadFeedSync(sdk);

        FeedPost post = createFeedPostSync(sdk, "Like Target", "<p>Like me</p>");
        assertNotNull(post);

        // Reload so postsById is populated (createPost doesn't update postsById)
        loadFeedSync(sdk);

        likeFeedPostSync(sdk, post.getId());

        assertTrue("User should have reacted to post",
                sdk.hasUserReactedToPost(post.getId(), "l"));
        assertEquals("Like count should be 1", 1, sdk.getPostLikeCount(post.getId()));
    }

    @Test
    public void testUnlikePost() throws Exception {
        FastCommentsFeedSDK sdk = makeFeedSDK("testUnlikePost");
        loadFeedSync(sdk);

        FeedPost post = createFeedPostSync(sdk, "Unlike Target", "<p>Like then unlike</p>");
        assertNotNull(post);

        // Reload so postsById is populated
        loadFeedSync(sdk);

        // Like
        likeFeedPostSync(sdk, post.getId());
        assertTrue(sdk.hasUserReactedToPost(post.getId(), "l"));

        // Unlike (toggle)
        likeFeedPostSync(sdk, post.getId());
        assertFalse("User should no longer have reacted after toggle",
                sdk.hasUserReactedToPost(post.getId(), "l"));
        assertEquals("Like count should be 0 after toggle", 0, sdk.getPostLikeCount(post.getId()));
    }

    @Test
    public void testSaveRestorePaginationState() throws Exception {
        FastCommentsFeedSDK sdk = makeFeedSDK("testSaveRestorePaginationState");
        loadFeedSync(sdk);

        createFeedPostSync(sdk, "Post 1", "<p>Content 1</p>");
        createFeedPostSync(sdk, "Post 2", "<p>Content 2</p>");

        // Reload so postsById is populated
        loadFeedSync(sdk);

        // Like a post to test reaction state
        FeedPost post = sdk.getFeedPosts().get(0);
        likeFeedPostSync(sdk, post.getId());

        // Save state
        FastCommentsFeedSDK.FeedState state = sdk.savePaginationState();
        assertNotNull(state);
        assertNotNull(state.getFeedPosts());
        assertTrue("Should have feed posts in saved state", state.getFeedPosts().size() >= 2);

        // Restore into new SDK
        FastCommentsFeedSDK sdk2 = makeFeedSDK("testSaveRestorePaginationState2");
        sdk2.restorePaginationState(state);

        assertNotNull(sdk2.getFeedPosts());
        assertEquals("Feed posts count should match", state.getFeedPosts().size(), sdk2.getFeedPosts().size());
    }

    @Test
    public void testCreateCommentsSDKForPost() throws Exception {
        FastCommentsFeedSDK sdk = makeFeedSDK("testCreateCommentsSDKForPost");
        loadFeedSync(sdk);

        FeedPost post = createFeedPostSync(sdk, "Post with Comments", "<p>Discuss</p>");
        assertNotNull(post);

        FastCommentsSDK commentsSDK = sdk.createCommentsSDKForPost(post);
        assertNotNull(commentsSDK);
        assertEquals("Comments SDK tenantId should match",
                getTenantId(), commentsSDK.getConfig().tenantId);
        assertTrue("Comments SDK urlId should start with 'post:'",
                commentsSDK.getConfig().urlId.startsWith("post:"));
    }

    @Test
    public void testFeedPostHasContent() throws Exception {
        FastCommentsFeedSDK sdk = makeFeedSDK("testFeedPostHasContent");
        loadFeedSync(sdk);

        FeedPost post = createFeedPostSync(sdk, "My Title", "<p>My Content</p>");
        assertNotNull(post);

        // Verify post is in feed
        boolean found = false;
        for (FeedPost fp : sdk.getFeedPosts()) {
            if (post.getId().equals(fp.getId())) {
                found = true;
                break;
            }
        }
        assertTrue("Created post should be in feed list", found);
    }
}
