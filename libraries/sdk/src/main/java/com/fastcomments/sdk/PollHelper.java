package com.fastcomments.sdk;

import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.CommentPoll;
import com.fastcomments.model.CommentPollOption;
import com.fastcomments.model.PollCreationMode;
import com.fastcomments.model.PollPrivacy;
import com.fastcomments.model.PollVotingMode;
import com.fastcomments.model.PublicComment;
import com.fastcomments.model.UserSessionInfo;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Poll rules shared by the poll card, the composer and the SDK. Mirrors the web widget's polls extension.
 */
final class PollHelper {

    private PollHelper() {}

    static boolean isClosed(CommentPoll poll) {
        return poll.getClosesAt() != null && !poll.getClosesAt().isAfter(OffsetDateTime.now());
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
     * The author can withhold tallies until you answer. The server already zeroes the counts in that case, so
     * this only decides what the card renders.
     */
    static boolean resultsLocked(PublicComment comment) {
        CommentPoll poll = comment.getPoll();
        return Boolean.TRUE.equals(poll.getRequireVoteToSeeResults()) && !hasVoted(comment) && !isClosed(poll);
    }

    static boolean isShowingResults(RenderableComment comment) {
        PublicComment publicComment = comment.getComment();
        if (resultsLocked(publicComment)) {
            return false;
        }
        if (comment.pollShowResults != null) {
            return comment.pollShowResults;
        }
        return hasVoted(publicComment) || isClosed(publicComment.getPoll());
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
        if (Boolean.TRUE.equals(config.useSingleLineCommentInput)) {
            return false;
        }
        if (config.pollCreation == PollCreationMode.NUMBER_2) {
            return true;
        }
        return config.pollCreation == PollCreationMode.NUMBER_1 && isSiteAdmin;
    }

    static boolean hasSameOptionIds(List<CommentPollOption> a, List<CommentPollOption> b) {
        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(a.get(i).getId(), b.get(i).getId())) {
                return false;
            }
        }
        return true;
    }
}
