package com.fastcomments.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fastcomments.model.CommentPollInput;
import java.time.Instant;
import java.util.Arrays;
import org.junit.Test;

public class PollDraftTest {

    @Test
    public void startsWithTwoEmptyOptions() {
        PollDraft draft = new PollDraft();
        assertEquals(Arrays.asList("", ""), draft.options);
        assertEquals(0, draft.closesDays);
        assertEquals(0, draft.privacy);
    }

    @Test
    public void validationMatchesTheWebBuilder() {
        PollDraft draft = new PollDraft();
        assertEquals(R.string.fastcomments_poll_error_question_required, draft.validate());

        draft.question = "  Favorite?  ";
        assertEquals(R.string.fastcomments_poll_error_min_options, draft.validate());

        draft.options.set(0, "Red");
        draft.options.set(1, "   ");
        assertEquals(R.string.fastcomments_poll_error_min_options, draft.validate());

        draft.options.set(1, "Blue");
        assertEquals(0, draft.validate());
    }

    @Test
    public void inputDropsBlankOptionsAndDefaults() {
        PollDraft draft = new PollDraft();
        draft.question = " Favorite? ";
        draft.options.set(0, " Red ");
        draft.options.set(1, "");
        draft.options.add("Blue");

        CommentPollInput input = draft.toInput(0);
        assertEquals("Favorite?", input.getQuestion());
        assertEquals(Arrays.asList("Red", "Blue"), input.getOptions());
        assertNull(input.getClosesAt());
        assertNull(input.getPrivacy());
        assertNull(input.getRequireVoteToSeeResults());
    }

    @Test
    public void inputCarriesDurationPrivacyAndVoteLock() {
        PollDraft draft = new PollDraft();
        draft.question = "Q";
        draft.options.set(0, "A");
        draft.options.set(1, "B");
        draft.closesDays = 3;
        draft.privacy = 2;
        draft.requireVoteToSeeResults = true;

        long now = 1_700_000_000_000L;
        CommentPollInput input = draft.toInput(now);
        assertEquals(Instant.ofEpochMilli(now + 3 * 86_400_000L).toString(), input.getClosesAt());
        assertEquals(Integer.valueOf(2), input.getPrivacy());
        assertTrue(input.getRequireVoteToSeeResults());
    }

    @Test
    public void collapseWhitespaceFlattensPastedText() {
        assertEquals("a b c", PollDraft.collapseWhitespace("  a\n\tb   c "));
    }
}
