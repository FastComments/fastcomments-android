package com.fastcomments.sdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.fastcomments.model.CommentPollInput;
import com.fastcomments.model.PollPrivacy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The poll being written in the comment composer. Limits match the server's validation. Apps posting polls from
 * their own UI pass a {@link CommentPollInput} to {@link FastCommentsSDK#postComment} instead.
 */
final class PollDraft {

    static final int MIN_OPTIONS = 2;
    static final int MAX_OPTIONS = 10;
    static final int MAX_QUESTION_LENGTH = 200;
    static final int MAX_OPTION_LENGTH = 100;

    private static final long DAY_MS = 86_400_000L;

    /** How long the poll stays open; the web builder's choices. */
    enum Duration {
        NEVER(0),
        ONE_DAY(1),
        THREE_DAYS(3),
        ONE_WEEK(7);

        final int days;

        Duration(int days) {
            this.days = days;
        }
    }

    enum Error {
        QUESTION_REQUIRED(R.string.fastcomments_poll_error_question_required),
        MIN_OPTIONS(R.string.fastcomments_poll_error_min_options);

        @StringRes
        final int message;

        Error(@StringRes int message) {
            this.message = message;
        }
    }

    private String question = "";
    private final List<String> options = new ArrayList<>(Arrays.asList("", ""));
    private Duration duration = Duration.NEVER;
    private PollPrivacy privacy = PollPrivacy.NUMBER_0;
    private boolean requireVoteToSeeResults = false;

    @NonNull
    String getQuestion() {
        return question;
    }

    void setQuestion(@NonNull String question) {
        this.question = question;
    }

    @NonNull
    List<String> getOptions() {
        return Collections.unmodifiableList(options);
    }

    void setOption(int index, @NonNull String text) {
        options.set(index, text);
    }

    /**
     * @return false when the poll already has {@link #MAX_OPTIONS}.
     */
    boolean addOption() {
        if (options.size() >= MAX_OPTIONS) {
            return false;
        }
        options.add("");
        return true;
    }

    /**
     * @return false when the poll is down to {@link #MIN_OPTIONS}.
     */
    boolean removeOption(int index) {
        if (options.size() <= MIN_OPTIONS) {
            return false;
        }
        options.remove(index);
        return true;
    }

    @NonNull
    Duration getDuration() {
        return duration;
    }

    void setDuration(@NonNull Duration duration) {
        this.duration = duration;
    }

    @NonNull
    PollPrivacy getPrivacy() {
        return privacy;
    }

    void setPrivacy(@NonNull PollPrivacy privacy) {
        this.privacy = privacy;
    }

    boolean isRequireVoteToSeeResults() {
        return requireVoteToSeeResults;
    }

    void setRequireVoteToSeeResults(boolean requireVoteToSeeResults) {
        this.requireVoteToSeeResults = requireVoteToSeeResults;
    }

    /**
     * @return null when the draft can be posted, otherwise what to fix.
     */
    @Nullable
    Error validate() {
        if (question.trim().isEmpty()) {
            return Error.QUESTION_REQUIRED;
        }
        if (getFilledOptions().size() < MIN_OPTIONS) {
            return Error.MIN_OPTIONS;
        }
        return null;
    }

    @NonNull
    CommentPollInput toInput(long nowMillis) {
        CommentPollInput input =
                new CommentPollInput().question(question.trim()).options(getFilledOptions());
        if (duration != Duration.NEVER) {
            input.setClosesAt(
                    Instant.ofEpochMilli(nowMillis + duration.days * DAY_MS).toString());
        }
        if (privacy != PollPrivacy.NUMBER_0) {
            input.setPrivacy(privacy.getValue());
        }
        if (requireVoteToSeeResults) {
            input.setRequireVoteToSeeResults(true);
        }
        return input;
    }

    private List<String> getFilledOptions() {
        List<String> filled = new ArrayList<>(options.size());
        for (String option : options) {
            String trimmed = option.trim();
            if (!trimmed.isEmpty()) {
                filled.add(trimmed);
            }
        }
        return filled;
    }
}
