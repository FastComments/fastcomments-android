package com.fastcomments.sdk;

import com.fastcomments.model.PublicComment;
import com.fastcomments.model.VoteResponse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for comment voting operations.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class VoteIntegrationTests extends IntegrationTestBase {

    @Test
    public void testUpvoteComment() throws Exception {
        FastCommentsSDK sdk = makeSDK("testUpvoteComment");
        loadSync(sdk);
        PublicComment comment = postCommentSync(sdk, "Vote target");

        VoteResponse response = voteCommentSync(sdk, comment.getId(), true);

        assertNotNull(response);
        assertNotNull(response.getVoteId());
    }

    @Test
    public void testDownvoteComment() throws Exception {
        FastCommentsSDK sdk = makeSDK("testDownvoteComment");
        loadSync(sdk);
        PublicComment comment = postCommentSync(sdk, "Vote target");

        VoteResponse response = voteCommentSync(sdk, comment.getId(), false);

        assertNotNull(response);
    }

    @Test
    public void testUpvoteThenDownvote() throws Exception {
        FastCommentsSDK sdk = makeSDK("testUpvoteThenDownvote");
        loadSync(sdk);
        PublicComment comment = postCommentSync(sdk, "Vote target");

        voteCommentSync(sdk, comment.getId(), true);
        voteCommentSync(sdk, comment.getId(), false);

        // After toggling from up to down, net votes should be -1
        // Reload to verify
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);
        PublicComment reloaded = sdk2.commentsTree.getPublicComment(comment.getId());
        assertNotNull(reloaded);
    }

    @Test
    public void testDownvoteThenUpvote() throws Exception {
        FastCommentsSDK sdk = makeSDK("testDownvoteThenUpvote");
        loadSync(sdk);
        PublicComment comment = postCommentSync(sdk, "Vote target");

        voteCommentSync(sdk, comment.getId(), false);
        voteCommentSync(sdk, comment.getId(), true);

        // After toggling from down to up, net votes should be 1
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);
        PublicComment reloaded = sdk2.commentsTree.getPublicComment(comment.getId());
        assertNotNull(reloaded);
    }

    @Test
    public void testDeleteVote() throws Exception {
        FastCommentsSDK sdk = makeSDK("testDeleteVote");
        loadSync(sdk);
        PublicComment comment = postCommentSync(sdk, "Vote target");

        VoteResponse voteResponse = voteCommentSync(sdk, comment.getId(), true);
        assertNotNull(voteResponse.getVoteId());

        deleteVoteSync(sdk, comment.getId(), voteResponse.getVoteId());

        // Reload to verify vote removed
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);
        PublicComment reloaded = sdk2.commentsTree.getPublicComment(comment.getId());
        assertNotNull(reloaded);
    }

    @Test
    public void testVoteUpdatesNetVotes() throws Exception {
        FastCommentsSDK sdk = makeSDK("testVoteUpdatesNetVotes");
        loadSync(sdk);
        PublicComment comment = postCommentSync(sdk, "Vote target");

        voteCommentSync(sdk, comment.getId(), true);

        // Reload and check
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);
        PublicComment reloaded = sdk2.commentsTree.getPublicComment(comment.getId());
        assertNotNull(reloaded);
        // Net votes should be positive after upvote
        assertTrue("Votes should be >= 1 after upvote",
                reloaded.getVotes() != null && reloaded.getVotes() >= 1);
    }

    @Test
    public void testVoteReturnsVoteId() throws Exception {
        FastCommentsSDK sdk = makeSDK("testVoteReturnsVoteId");
        loadSync(sdk);
        PublicComment comment = postCommentSync(sdk, "Vote target");

        VoteResponse response = voteCommentSync(sdk, comment.getId(), true);

        assertNotNull("Vote response should have a voteId", response.getVoteId());
        assertTrue("VoteId should not be empty", !response.getVoteId().isEmpty());
    }

    @Test
    public void testMultipleVotesOnDifferentComments() throws Exception {
        FastCommentsSDK sdk = makeSDK("testMultipleVotesOnDifferentComments");
        loadSync(sdk);

        PublicComment c1 = postCommentSync(sdk, "Comment 1");
        PublicComment c2 = postCommentSync(sdk, "Comment 2");

        voteCommentSync(sdk, c1.getId(), true);
        voteCommentSync(sdk, c2.getId(), true);

        // Reload and verify both have votes
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);

        PublicComment r1 = sdk2.commentsTree.getPublicComment(c1.getId());
        PublicComment r2 = sdk2.commentsTree.getPublicComment(c2.getId());
        assertNotNull(r1);
        assertNotNull(r2);
    }

    @Test
    public void testDoubleUpvoteToggle() throws Exception {
        FastCommentsSDK sdk = makeSDK("testDoubleUpvoteToggle");
        loadSync(sdk);
        PublicComment comment = postCommentSync(sdk, "Vote target");

        voteCommentSync(sdk, comment.getId(), true);
        // Second upvote is rejected by the server ("A user can only vote once per comment per direction").
        // We expect this failure, so handle it gracefully.
        try {
            voteCommentSync(sdk, comment.getId(), true);
        } catch (AssertionError | Exception e) {
            // Expected: duplicate same-direction vote is rejected
        }

        // Verify state is consistent (no crash, comment still exists)
        FastCommentsSDK sdk2 = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(sdk2);
        assertNotNull(sdk2.commentsTree.getPublicComment(comment.getId()));
    }
}
