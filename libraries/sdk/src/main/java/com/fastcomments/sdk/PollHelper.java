package com.fastcomments.sdk;

import androidx.annotation.VisibleForTesting;
import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.CommentPoll;
import com.fastcomments.model.CommentPollOption;
import com.fastcomments.model.PollCreationMode;
import com.fastcomments.model.PollPrivacy;
import com.fastcomments.model.PollVotingMode;
import com.fastcomments.model.PublicComment;
import com.fastcomments.model.UserSessionInfo;
import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Poll rules shared by the poll card, the composer and the SDK. Mirrors the web widget's polls extension.
 */
final class PollHelper {

    // Replaced in tests to move time past a poll's close.
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    static Clock clock = Clock.systemUTC();

    private PollHelper() {}

    static OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    static boolean isClosed(CommentPoll poll) {
        return poll.getClosesAt() != null && !poll.getClosesAt().isAfter(now());
    }

    static int getPercent(Integer votes, Integer total) {
        if (votes == null || total == null || total <= 0) {
            return 0;
        }
        return Math.round(votes * 100f / total);
    }

    static boolean hasVoted(PublicComment comment) {
        return comment.getMyPollVotes() != null && !comment.getMyPollVotes().isEmpty();
    }

    static boolean isSelected(PublicComment comment, String optionId) {
        return hasVoted(comment) && comment.getMyPollVotes().contains(optionId);
    }

    /**
     * The author can withhold tallies until you answer. The page load zeroes the counts, but live poll-update
     * events carry the real ones to everyone, so this is what keeps them off screen. That is by design: the lock is
     * a reveal-after-voting UX, not a secret.
     */
    static boolean resultsLocked(PublicComment comment) {
        CommentPoll poll = comment.getPoll();
        return Boolean.TRUE.equals(poll.getRequireVoteToSeeResults()) && !hasVoted(comment) && !isClosed(poll);
    }

    /**
     * Whether this poll's option counts are the zeroed ones from the page load. They stay that way until a live
     * tally arrives, and closing sends none, so a poll that closes before anyone votes again goes stale.
     */
    static boolean countsWithheld(PublicComment comment) {
        CommentPoll poll = comment.getPoll();
        if (poll == null) {
            return false;
        }
        if (resultsLocked(comment)) {
            return true;
        }
        // The total is the options' sum, so votes with every option at 0 means the server zeroed them. That happens
        // when the device clock already sees the poll closed but the server didn't yet.
        if (poll.getTotalVotes() == null || poll.getTotalVotes() <= 0 || poll.getOptions() == null) {
            return false;
        }
        for (CommentPollOption option : poll.getOptions()) {
            if (option.getVotes() != null && option.getVotes() > 0) {
                return false;
            }
        }
        return true;
    }

    static boolean isShowingResults(RenderableComment comment) {
        PublicComment publicComment = comment.getComment();
        if (resultsLocked(publicComment)) {
            return false;
        }
        // Closing hides the results toggles, so a saved "hide" would leave the viewer no way back to them.
        if (isClosed(publicComment.getPoll())) {
            return !comment.pollCountsWithheld;
        }
        if (comment.pollShowResults != null) {
            return comment.pollShowResults;
        }
        return hasVoted(publicComment);
    }

    /**
     * An anonymous session still has a user object, so holding one is not the same as being logged in.
     */
    static boolean canVote(CommentWidgetConfig config, UserSessionInfo currentUser, CommentPoll poll) {
        if (isClosed(poll)) {
            return false;
        }
        if (config.pollVoting == PollVotingMode.NUMBER_1) {
            return true;
        }
        return currentUser != null && !Boolean.TRUE.equals(currentUser.getIsAnonSession());
    }

    static boolean canSeeVoters(CommentPoll poll) {
        return poll.getPrivacy() == PollPrivacy.NUMBER_1 || poll.getPrivacy() == PollPrivacy.NUMBER_2;
    }

    static boolean canCreatePoll(CommentWidgetConfig config, boolean isSiteAdmin) {
        if (config.pollCreation == PollCreationMode.NUMBER_2) {
            return true;
        }
        return config.pollCreation == PollCreationMode.NUMBER_1 && isSiteAdmin;
    }
}
