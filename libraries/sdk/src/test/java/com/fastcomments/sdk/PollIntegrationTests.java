package com.fastcomments.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fastcomments.model.APIError;
import com.fastcomments.model.CommentPoll;
import com.fastcomments.model.CommentPollInput;
import com.fastcomments.model.GetPollVoterNamesSuccessResponse;
import com.fastcomments.model.PollPrivacy;
import com.fastcomments.model.PublicComment;
import com.fastcomments.model.VotePollResponse;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Polls against the real API: posting a comment with a poll, voting, live tallies and the voter list.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class PollIntegrationTests extends IntegrationTestBase {

    private void enablePolls(int pollVoting) throws Exception {
        createCustomConfigSync(new JSONObject().put("pollCreation", 2).put("pollVoting", pollVoting));
    }

    // Comments load with the server's config, so this also waits out any config caching after it was saved.
    private void loadWithPolls(FastCommentsSDK sdk) throws Exception {
        waitFor(20000, 1000, () -> {
            try {
                loadSync(sdk);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return sdk.canCreatePoll();
        });
    }

    private static CommentPollInput makePollInput(PollPrivacy privacy) {
        PollDraft draft = new PollDraft();
        draft.question = "Best fruit?";
        draft.options.set(0, "Apple");
        draft.options.set(1, "Banana");
        draft.privacy = privacy.getValue();
        return draft.toInput(System.currentTimeMillis());
    }

    private PublicComment postPollCommentSync(FastCommentsSDK sdk, CommentPollInput poll) throws Exception {
        AtomicReference<PublicComment> result = new AtomicReference<>();
        AtomicReference<APIError> error = new AtomicReference<>();
        sdk.postComment("Vote please", null, null, poll, new FCCallback<PublicComment>() {
            @Override
            public boolean onFailure(APIError e) {
                error.set(e);
                return CONSUME;
            }

            @Override
            public boolean onSuccess(PublicComment comment) {
                result.set(comment);
                return CONSUME;
            }
        });
        waitFor(() -> result.get() != null || error.get() != null);
        if (error.get() != null) {
            fail("postComment with poll failed: " + error.get().getCode() + " "
                    + error.get().getReason());
        }
        sdk.addComment(result.get(), true);
        return result.get();
    }

    private Object votePollSync(FastCommentsSDK sdk, String commentId, String optionId) throws Exception {
        AtomicReference<Object> result = new AtomicReference<>();
        sdk.votePoll(commentId, optionId, new FCCallback<VotePollResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                result.set(error);
                return CONSUME;
            }

            @Override
            public boolean onSuccess(VotePollResponse response) {
                result.set(response);
                return CONSUME;
            }
        });
        waitFor(() -> result.get() != null);
        return result.get();
    }

    private VotePollResponse votePollExpectSuccess(FastCommentsSDK sdk, String commentId, String optionId)
            throws Exception {
        Object result = votePollSync(sdk, commentId, optionId);
        if (result instanceof APIError) {
            fail("votePoll failed: " + ((APIError) result).getCode());
        }
        assertTrue(result instanceof VotePollResponse);
        return (VotePollResponse) result;
    }

    @Test
    public void testPostedPollLoadsTyped() throws Exception {
        enablePolls(0);
        FastCommentsSDK sdk = makeSDK("testPostedPollLoadsTyped");
        loadWithPolls(sdk);

        PublicComment posted = postPollCommentSync(sdk, makePollInput(PollPrivacy.NUMBER_2));
        assertNotNull(posted.getPoll());
        assertEquals(2, posted.getPoll().getOptions().size());

        FastCommentsSDK reader = makeSDKWithUrlId(sdk.getConfig().urlId);
        loadSync(reader);
        CommentPoll poll = reader.commentsTree.getPublicComment(posted.getId()).getPoll();
        assertNotNull(poll);
        assertEquals("Best fruit?", poll.getQuestion());
        assertEquals("Apple", poll.getOptions().get(0).getLabel());
        assertEquals("Banana", poll.getOptions().get(1).getLabel());
        assertEquals(Integer.valueOf(0), poll.getTotalVotes());
        assertEquals(PollPrivacy.NUMBER_2, poll.getPrivacy());
    }

    @Test
    public void testVoteThenChangeVote() throws Exception {
        enablePolls(0);
        String ssoToken = makeSSOToken();
        FastCommentsSDK sdk = makeSDKWithUrlId(makeUrlId("testVoteThenChangeVote"), ssoToken);
        loadWithPolls(sdk);
        PublicComment posted = postPollCommentSync(sdk, makePollInput(PollPrivacy.NUMBER_0));
        String first = posted.getPoll().getOptions().get(0).getId();
        String second = posted.getPoll().getOptions().get(1).getId();

        VotePollResponse vote = votePollExpectSuccess(sdk, posted.getId(), first);
        assertEquals(Collections.singletonList(first), vote.getMyPollVotes());
        assertEquals(Integer.valueOf(1), vote.getPoll().getTotalVotes());
        // The tree's comment is updated before the callback runs.
        PublicComment inTree = sdk.commentsTree.getPublicComment(posted.getId());
        assertEquals(Collections.singletonList(first), inTree.getMyPollVotes());
        assertEquals(Integer.valueOf(1), inTree.getPoll().getOptions().get(0).getVotes());

        VotePollResponse changed = votePollExpectSuccess(sdk, posted.getId(), second);
        assertEquals(Collections.singletonList(second), changed.getMyPollVotes());
        assertEquals(Integer.valueOf(1), changed.getPoll().getTotalVotes());
        assertEquals(Integer.valueOf(0), changed.getPoll().getOptions().get(0).getVotes());
        assertEquals(Integer.valueOf(1), changed.getPoll().getOptions().get(1).getVotes());

        FastCommentsSDK again = makeSDKWithUrlId(sdk.getConfig().urlId, ssoToken);
        loadSync(again);
        assertEquals(
                Collections.singletonList(second),
                again.commentsTree.getPublicComment(posted.getId()).getMyPollVotes());
    }

    @Test
    public void testOtherViewersSeeLiveTallies() throws Exception {
        enablePolls(0);
        FastCommentsSDK author = makeSDK("testOtherViewersSeeLiveTallies");
        loadWithPolls(author);
        PublicComment posted = postPollCommentSync(author, makePollInput(PollPrivacy.NUMBER_0));

        FastCommentsSDK viewer = makeSDKWithUrlId(author.getConfig().urlId);
        loadSync(viewer);
        assertEquals(
                Integer.valueOf(0),
                viewer.commentsTree.getPublicComment(posted.getId()).getPoll().getTotalVotes());
        // Let the viewer's websocket connect before the vote is broadcast.
        Thread.sleep(3000);

        votePollExpectSuccess(
                author, posted.getId(), posted.getPoll().getOptions().get(1).getId());

        waitFor(20000, 200, () -> {
            CommentPoll poll =
                    viewer.commentsTree.getPublicComment(posted.getId()).getPoll();
            return poll.getTotalVotes() == 1 && poll.getOptions().get(1).getVotes() == 1;
        });
        // Live events carry tallies only, never the viewer's own vote.
        PublicComment seen = viewer.commentsTree.getPublicComment(posted.getId());
        assertTrue(seen.getMyPollVotes() == null || seen.getMyPollVotes().isEmpty());
    }

    @Test
    public void testPublicPollListsVoters() throws Exception {
        enablePolls(0);
        FastCommentsSDK sdk = makeSDK("testPublicPollListsVoters");
        loadWithPolls(sdk);
        PublicComment posted = postPollCommentSync(sdk, makePollInput(PollPrivacy.NUMBER_2));
        votePollExpectSuccess(
                sdk, posted.getId(), posted.getPoll().getOptions().get(1).getId());

        AtomicReference<GetPollVoterNamesSuccessResponse> result = new AtomicReference<>();
        AtomicReference<APIError> error = new AtomicReference<>();
        sdk.getPollVoters(posted.getId(), 0, null, new FCCallback<GetPollVoterNamesSuccessResponse>() {
            @Override
            public boolean onFailure(APIError e) {
                error.set(e);
                return CONSUME;
            }

            @Override
            public boolean onSuccess(GetPollVoterNamesSuccessResponse response) {
                result.set(response);
                return CONSUME;
            }
        });
        waitFor(() -> result.get() != null || error.get() != null);
        if (error.get() != null) {
            fail("getPollVoters failed: " + error.get().getCode());
        }
        assertEquals(Integer.valueOf(1), result.get().getTotal());
        assertEquals(1, result.get().getVoters().size());
        assertEquals("Banana", result.get().getVoters().get(0).getOptionLabel());
    }

    @Test
    public void testAnonymousVoteNeedsLoginByDefault() throws Exception {
        enablePolls(0);
        FastCommentsSDK author = makeSDK("testAnonymousVoteNeedsLoginByDefault");
        loadWithPolls(author);
        PublicComment posted = postPollCommentSync(author, makePollInput(PollPrivacy.NUMBER_0));

        FastCommentsSDK anonymous = makeSDKWithUrlId(author.getConfig().urlId, null);
        loadSync(anonymous);
        assertTrue(!PollHelper.canVote(
                anonymous.getConfig(),
                anonymous.getCurrentUser(),
                anonymous.commentsTree.getPublicComment(posted.getId()).getPoll()));

        Object result = votePollSync(
                anonymous, posted.getId(), posted.getPoll().getOptions().get(0).getId());
        assertTrue(result instanceof APIError);
        assertEquals("poll-login-required", ((APIError) result).getCode());
    }

    @Test
    public void testPollsOffMeansNoPollButton() throws Exception {
        FastCommentsSDK sdk = makeSDK("testPollsOffMeansNoPollButton");
        loadSync(sdk);
        assertTrue(!sdk.canCreatePoll());
    }
}
