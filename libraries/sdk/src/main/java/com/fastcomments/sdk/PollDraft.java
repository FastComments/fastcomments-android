package com.fastcomments.sdk;

import com.fastcomments.model.CommentPollInput;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A poll being written in the comment composer. Limits match the server's validation.
 */
public class PollDraft {

    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 10;
    public static final int MAX_QUESTION_LENGTH = 200;
    public static final int MAX_OPTION_LENGTH = 100;
    public static final int[] CLOSES_DAYS = {0, 1, 3, 7};

    private static final long DAY_MS = 86_400_000L;

    public String question = "";
    public final List<String> options = new ArrayList<>(Arrays.asList("", ""));
    public int closesDays = 0;
    /** PollPrivacy value: 0 anonymous, 1 admins can see who voted, 2 public. */
    public int privacy = 0;

    public boolean requireVoteToSeeResults = false;

    public static String collapseWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    /**
     * @return 0 when the draft can be posted, otherwise the error string resource to show.
     */
    public int validate() {
        if (question.trim().isEmpty()) {
            return R.string.fastcomments_poll_error_question_required;
        }
        if (getFilledOptions().size() < MIN_OPTIONS) {
            return R.string.fastcomments_poll_error_min_options;
        }
        return 0;
    }

    public CommentPollInput toInput(long nowMillis) {
        CommentPollInput input =
                new CommentPollInput().question(question.trim()).options(getFilledOptions());
        if (closesDays > 0) {
            input.setClosesAt(
                    Instant.ofEpochMilli(nowMillis + closesDays * DAY_MS).toString());
        }
        if (privacy != 0) {
            input.setPrivacy(privacy);
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
