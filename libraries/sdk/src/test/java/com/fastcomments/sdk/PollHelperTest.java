package com.fastcomments.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.CommentPoll;
import com.fastcomments.model.CommentPollOption;
import com.fastcomments.model.PollCreationMode;
import com.fastcomments.model.PollPrivacy;
import com.fastcomments.model.PollVotingMode;
import com.fastcomments.model.PublicComment;
import com.fastcomments.model.UserSessionInfo;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class PollHelperTest {

    static CommentPoll makePoll(int... votes) {
        CommentPoll poll = new CommentPoll().question("Best color?");
        int total = 0;
        for (int i = 0; i < votes.length; i++) {
            poll.addOptionsItem(
                    new CommentPollOption().id("o" + i).label("Option " + i).votes(votes[i]));
            total += votes[i];
        }
        return poll.totalVotes(total);
    }

    static PublicComment makePollComment(CommentPoll poll) {
        PublicComment comment = MockComment.make();
        comment.setPoll(poll);
        return comment;
    }

    @Test
    public void percentRoundsAndHandlesZeroTotal() {
        assertEquals(0, PollHelper.getPercent(0, 0));
        assertEquals(0, PollHelper.getPercent(null, null));
        assertEquals(33, PollHelper.getPercent(1, 3));
        assertEquals(67, PollHelper.getPercent(2, 3));
        assertEquals(100, PollHelper.getPercent(4, 4));
    }

    @Test
    public void closedOnlyWhenClosesAtHasPassed() {
        CommentPoll poll = makePoll(1, 2);
        assertFalse(PollHelper.isClosed(poll));
        poll.setClosesAt(OffsetDateTime.now().plusDays(1));
        assertFalse(PollHelper.isClosed(poll));
        poll.setClosesAt(OffsetDateTime.now().minusMinutes(1));
        assertTrue(PollHelper.isClosed(poll));
    }

    @Test
    public void resultsShowAfterVotingOrClosingUnlessOverridden() {
        RenderableComment comment = new RenderableComment(makePollComment(makePoll(1, 2)));
        assertFalse(PollHelper.isShowingResults(comment));

        comment.pollShowResults = true;
        assertTrue(PollHelper.isShowingResults(comment));
        comment.pollShowResults = null;

        comment.getComment().setMyPollVotes(Collections.singletonList("o1"));
        assertTrue(PollHelper.hasVoted(comment.getComment()));
        assertTrue(PollHelper.isSelected(comment.getComment(), "o1"));
        assertFalse(PollHelper.isSelected(comment.getComment(), "o0"));
        assertTrue(PollHelper.isShowingResults(comment));

        // "Change vote" hides results again even though the viewer has voted.
        comment.pollShowResults = false;
        assertFalse(PollHelper.isShowingResults(comment));

        RenderableComment closed = new RenderableComment(makePollComment(makePoll(1, 2)));
        closed.getComment().getPoll().setClosesAt(OffsetDateTime.now().minusDays(1));
        assertTrue(PollHelper.isShowingResults(closed));
    }

    @Test
    public void lockedResultsIgnoreTheShowResultsOverride() {
        CommentPoll poll = makePoll(0, 0).requireVoteToSeeResults(true).totalVotes(5);
        RenderableComment comment = new RenderableComment(makePollComment(poll));
        assertTrue(PollHelper.resultsLocked(comment.getComment()));
        comment.pollShowResults = true;
        assertFalse(PollHelper.isShowingResults(comment));

        comment.getComment().setMyPollVotes(Collections.singletonList("o0"));
        assertFalse(PollHelper.resultsLocked(comment.getComment()));
        assertTrue(PollHelper.isShowingResults(comment));

        poll.setClosesAt(OffsetDateTime.now().minusDays(1));
        comment.getComment().setMyPollVotes(null);
        assertFalse(PollHelper.resultsLocked(comment.getComment()));
    }

    @Test
    public void canVoteMirrorsTheWebGate() {
        CommentWidgetConfig config = new CommentWidgetConfig("tenant", "url");
        CommentPoll poll = makePoll(1, 1);

        assertFalse(PollHelper.canVote(config, null, poll));
        assertFalse(PollHelper.canVote(config, new UserSessionInfo().isAnonSession(true), poll));
        assertTrue(PollHelper.canVote(config, new UserSessionInfo().id("u1").authorized(true), poll));

        config.pollVoting = PollVotingMode.NUMBER_1;
        assertTrue(PollHelper.canVote(config, null, poll));

        poll.setClosesAt(OffsetDateTime.now().minusDays(1));
        assertFalse(PollHelper.canVote(config, null, poll));
    }

    @Test
    public void canCreatePollRespectsModeAndAdminFlag() {
        CommentWidgetConfig config = new CommentWidgetConfig("tenant", "url");
        assertFalse(PollHelper.canCreatePoll(config, true));

        config.pollCreation = PollCreationMode.NUMBER_0;
        assertFalse(PollHelper.canCreatePoll(config, true));

        config.pollCreation = PollCreationMode.NUMBER_1;
        assertFalse(PollHelper.canCreatePoll(config, false));
        assertTrue(PollHelper.canCreatePoll(config, true));

        config.pollCreation = PollCreationMode.NUMBER_2;
        assertTrue(PollHelper.canCreatePoll(config, false));

        config.useSingleLineCommentInput = true;
        assertFalse(PollHelper.canCreatePoll(config, true));
    }

    @Test
    public void votersAreOnlyListedForNamedPolls() {
        CommentPoll poll = makePoll(1);
        assertFalse(PollHelper.canSeeVoters(poll));
        poll.setPrivacy(PollPrivacy.NUMBER_0);
        assertFalse(PollHelper.canSeeVoters(poll));
        poll.setPrivacy(PollPrivacy.NUMBER_1);
        assertTrue(PollHelper.canSeeVoters(poll));
        poll.setPrivacy(PollPrivacy.NUMBER_2);
        assertTrue(PollHelper.canSeeVoters(poll));
    }

    @Test
    public void sameOptionIdsComparesIdsInOrder() {
        CommentPoll a = makePoll(1, 2);
        CommentPoll b = makePoll(5, 9);
        assertTrue(PollHelper.hasSameOptionIds(a.getOptions(), b.getOptions()));
        assertFalse(
                PollHelper.hasSameOptionIds(a.getOptions(), makePoll(1, 2, 3).getOptions()));
        assertFalse(PollHelper.hasSameOptionIds(
                a.getOptions(),
                Arrays.asList(b.getOptions().get(1), b.getOptions().get(0))));
    }
}
