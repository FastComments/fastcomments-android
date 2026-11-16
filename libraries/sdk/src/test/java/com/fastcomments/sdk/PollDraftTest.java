package com.fastcomments.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fastcomments.model.CommentPollInput;
import com.fastcomments.model.PollPrivacy;
import java.time.Instant;
import java.util.Arrays;
import org.junit.Test;

public class PollDraftTest {

    @Test
    public void startsWithTwoEmptyOptions() {
        PollDraft draft = new PollDraft();
        assertEquals(Arrays.asList("", ""), draft.getOptions());
        assertEquals(PollDraft.Duration.NEVER, draft.getDuration());
        assertEquals(PollPrivacy.NUMBER_0, draft.getPrivacy());
        assertFalse(draft.isRequireVoteToSeeResults());
    }

    @Test
    public void validationMatchesTheWebBuilder() {
        PollDraft draft = new PollDraft();
        assertEquals(PollDraft.Error.QUESTION_REQUIRED, draft.validate());
        assertEquals(R.string.fastcomments_poll_error_question_required, PollDraft.Error.QUESTION_REQUIRED.message);

        draft.setQuestion("  Favorite?  ");
        assertEquals(PollDraft.Error.MIN_OPTIONS, draft.validate());

        draft.setOption(0, "Red");
        draft.setOption(1, "   ");
        assertEquals(PollDraft.Error.MIN_OPTIONS, draft.validate());

        draft.setOption(1, "Blue");
        assertNull(draft.validate());
    }

    @Test
    public void optionCountStaysWithinTheServerLimits() {
        PollDraft draft = new PollDraft();
        assertFalse(draft.removeOption(0));
        assertEquals(PollDraft.MIN_OPTIONS, draft.getOptions().size());

        for (int i = draft.getOptions().size(); i < PollDraft.MAX_OPTIONS; i++) {
            assertTrue(draft.addOption());
        }
        assertFalse(draft.addOption());
        assertEquals(PollDraft.MAX_OPTIONS, draft.getOptions().size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void optionsAreReadOnlyOutside() {
        new PollDraft().getOptions().add("C");
    }

    @Test
    public void inputDropsBlankOptionsAndDefaults() {
        PollDraft draft = new PollDraft();
        draft.setQuestion(" Favorite? ");
        draft.setOption(0, " Red ");
        draft.setOption(1, "");
        draft.addOption();
        draft.setOption(2, "Blue");

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
        draft.setQuestion("Q");
        draft.setOption(0, "A");
        draft.setOption(1, "B");
        draft.setDuration(PollDraft.Duration.THREE_DAYS);
        draft.setPrivacy(PollPrivacy.NUMBER_2);
        draft.setRequireVoteToSeeResults(true);

        long now = 1_700_000_000_000L;
        CommentPollInput input = draft.toInput(now);
        assertEquals(Instant.ofEpochMilli(now + 3 * 86_400_000L).toString(), input.getClosesAt());
        assertEquals(Integer.valueOf(2), input.getPrivacy());
        assertTrue(input.getRequireVoteToSeeResults());
    }
}
