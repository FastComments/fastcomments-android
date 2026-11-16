package com.fastcomments.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.CommentPoll;
import com.fastcomments.model.CommentPollOption;
import com.fastcomments.model.LiveEvent;
import com.fastcomments.model.LiveEventType;
import com.fastcomments.model.PickCommentPollOptionsOrTotalVotesOrClosesAt;
import com.fastcomments.model.PublicComment;
import com.fastcomments.model.VotePollResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
public class PollVoteOrderingTest {

    private FastCommentsSDK sdk;
    private PublicComment comment;

    @Before
    public void setup() {
        sdk = new FastCommentsSDK(new CommentWidgetConfig("tenant", "url"), true);
        sdk.commentsTree.setAdapter(mock(CommentsAdapter.class));
        comment = PollHelperTest.makePollComment(PollHelperTest.makePoll(1, 1));
        sdk.commentsTree.build(new ArrayList<>(Collections.singletonList(comment)));
    }

    private static LiveEvent pollUpdate(String commentId, String broadcastId, int first, int second) {
        return new LiveEvent()
                .type(LiveEventType.POLL_UPDATE)
                .commentId(commentId)
                .broadcastId(broadcastId)
                .poll(new PickCommentPollOptionsOrTotalVotesOrClosesAt()
                        .options(Arrays.asList(
                                new CommentPollOption()
                                        .id("o0")
                                        .label("Option 0")
                                        .votes(first),
                                new CommentPollOption()
                                        .id("o1")
                                        .label("Option 1")
                                        .votes(second)))
                        .totalVotes(first + second));
    }

    private static VotePollResponse voteResponse(int first, int second) {
        return new VotePollResponse()
                .poll(PollHelperTest.makePoll(first, second))
                .myPollVotes(Collections.singletonList("o1"));
    }

    private CommentPoll poll() {
        return sdk.commentsTree.getPublicComment(comment.getId()).getPoll();
    }

    @Test
    public void ownPollUpdateIsStillApplied() {
        // Tally events are snapshots in server order, so the one our vote caused carries the latest counts.
        sdk.broadcastIdsSent.add("mine");
        sdk.handleLiveEvent(pollUpdate(comment.getId(), "mine", 1, 2));
        ShadowLooper.idleMainLooper();
        assertEquals(Integer.valueOf(3), poll().getTotalVotes());
    }

    @Test
    public void voteResponseKeepsTalliesThatArrivedFirst() {
        int updatesAtSend = sdk.getPollUpdateCount(comment.getId());
        sdk.handleLiveEvent(pollUpdate(comment.getId(), "someone-else", 2, 3));
        ShadowLooper.idleMainLooper();

        sdk.applyPollVote(comment.getId(), voteResponse(1, 2), updatesAtSend);
        assertEquals(Integer.valueOf(5), poll().getTotalVotes());
        assertEquals(Collections.singletonList("o1"), comment.getMyPollVotes());
    }

    @Test
    public void voteResponseAppliesWhenNoTalliesArrived() {
        int updatesAtSend = sdk.getPollUpdateCount(comment.getId());
        sdk.applyPollVote(comment.getId(), voteResponse(1, 2), updatesAtSend);
        assertEquals(Integer.valueOf(3), poll().getTotalVotes());
        assertEquals(Collections.singletonList("o1"), comment.getMyPollVotes());
    }

    private RenderableComment withheldPoll() {
        // The server zeroes each option for a viewer who hasn't voted on a results-after-voting poll.
        PublicComment locked = PollHelperTest.makePollComment(
                PollHelperTest.makePoll(0, 0).requireVoteToSeeResults(true).totalVotes(5));
        sdk.commentsTree.build(new ArrayList<>(Collections.singletonList(locked)));
        RenderableComment renderable = sdk.commentsTree.commentsById.get(locked.getId());
        assertTrue(renderable.pollCountsWithheld);
        return renderable;
    }

    @Test
    public void voteResponseCarriesTheRealCounts() {
        RenderableComment renderable = withheldPoll();
        sdk.applyPollVote(renderable.getComment().getId(), voteResponse(2, 3), 0);
        assertFalse(renderable.pollCountsWithheld);
    }

    private static PublicComment fetched(String id, CommentPoll poll) {
        PublicComment comment = PollHelperTest.makePollComment(poll.requireVoteToSeeResults(true)
                .closesAt(java.time.OffsetDateTime.now().minusMinutes(1)));
        comment.setId(id);
        return comment;
    }

    @Test
    public void refreshedPollCarriesTheRealCounts() {
        RenderableComment renderable = withheldPoll();
        String id = renderable.getComment().getId();
        PublicComment other = PollHelperTest.makePollComment(PollHelperTest.makePoll(9, 9));
        assertTrue(sdk.applyPollRefresh(id, Arrays.asList(other, fetched(id, PollHelperTest.makePoll(2, 3)))));
        assertFalse(renderable.pollCountsWithheld);
        assertEquals(
                Integer.valueOf(3),
                renderable.getComment().getPoll().getOptions().get(1).getVotes());
    }

    @Test
    public void refreshTheServerStillZeroedStaysWithheld() {
        RenderableComment renderable = withheldPoll();
        String id = renderable.getComment().getId();
        assertTrue(sdk.applyPollRefresh(
                id,
                Collections.singletonList(
                        fetched(id, PollHelperTest.makePoll(0, 0).totalVotes(7)))));
        assertTrue(renderable.pollCountsWithheld);
    }

    @Test
    public void refreshMissingTheCommentChangesNothing() {
        RenderableComment renderable = withheldPoll();
        assertFalse(sdk.applyPollRefresh(renderable.getComment().getId(), new ArrayList<>()));
        assertTrue(renderable.pollCountsWithheld);
    }

    @Test
    public void liveTalliesCarryTheRealCounts() {
        RenderableComment renderable = withheldPoll();
        sdk.handleLiveEvent(pollUpdate(renderable.getComment().getId(), "someone-else", 2, 3));
        ShadowLooper.idleMainLooper();
        assertFalse(renderable.pollCountsWithheld);
    }
}
