package com.fastcomments.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.PollCreationMode;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class PollBuilderViewTest {

    private PollBuilderView builder;
    private PollDraft draft;

    @Before
    public void setup() {
        builder = new PollBuilderView(RuntimeEnvironment.getApplication());
        draft = new PollDraft();
        builder.bind(draft);
    }

    private ViewGroup options() {
        return builder.findViewById(R.id.pollBuilderOptions);
    }

    private View optionRow(int index) {
        return options().getChildAt(index);
    }

    private EditText optionInput(int index) {
        return optionRow(index).findViewById(R.id.pollBuilderOptionInput);
    }

    @Test
    public void startsWithTwoRequiredOptions() {
        assertEquals(2, options().getChildCount());
        assertEquals("Option 1", optionInput(0).getHint().toString());
        assertEquals("Option 2", optionInput(1).getHint().toString());
        assertEquals(
                View.INVISIBLE,
                optionRow(0).findViewById(R.id.pollBuilderOptionRemove).getVisibility());
        assertEquals(
                View.VISIBLE, builder.findViewById(R.id.pollBuilderAddOption).getVisibility());
        assertEquals(
                "Ask a question…",
                ((EditText) builder.findViewById(R.id.pollBuilderQuestion))
                        .getHint()
                        .toString());
    }

    @Test
    public void typingUpdatesTheDraft() {
        ((EditText) builder.findViewById(R.id.pollBuilderQuestion)).setText("Lunch?");
        optionInput(0).setText("Pizza");
        optionInput(1).setText("Tacos");
        assertEquals("Lunch?", draft.question);
        assertEquals("Pizza", draft.options.get(0));
        assertEquals("Tacos", draft.options.get(1));
    }

    @Test
    public void addAndRemoveOptionsWithinLimits() {
        optionInput(0).setText("A");
        optionInput(1).setText("B");
        builder.findViewById(R.id.pollBuilderAddOption).performClick();
        assertEquals(3, options().getChildCount());
        assertEquals(3, draft.options.size());
        assertEquals(
                View.VISIBLE,
                optionRow(0).findViewById(R.id.pollBuilderOptionRemove).getVisibility());

        optionRow(0).findViewById(R.id.pollBuilderOptionRemove).performClick();
        assertEquals(2, options().getChildCount());
        assertEquals("B", draft.options.get(0));
        assertEquals("B", optionInput(0).getText().toString());

        for (int i = 0; i < 20; i++) {
            builder.findViewById(R.id.pollBuilderAddOption).performClick();
        }
        assertEquals(PollDraft.MAX_OPTIONS, options().getChildCount());
        assertEquals(View.GONE, builder.findViewById(R.id.pollBuilderAddOption).getVisibility());
    }

    @Test
    public void durationPillsAreExclusive() {
        ViewGroup durations = builder.findViewById(R.id.pollBuilderDurations);
        assertEquals(PollDraft.CLOSES_DAYS.length, durations.getChildCount());
        assertTrue(durations.getChildAt(0).isSelected());
        assertEquals(
                "No end date", ((TextView) durations.getChildAt(0)).getText().toString());

        durations.getChildAt(2).performClick();
        assertEquals(3, draft.closesDays);
        assertFalse(durations.getChildAt(0).isSelected());
        assertTrue(durations.getChildAt(2).isSelected());
    }

    @Test
    public void requireVoteAndPrivacyUpdateTheDraft() {
        CheckBox requireVote = builder.findViewById(R.id.pollBuilderRequireVote);
        assertEquals("Require Vote to See Results", requireVote.getText().toString());
        requireVote.performClick();
        assertTrue(draft.requireVoteToSeeResults);

        Spinner privacy = builder.findViewById(R.id.pollBuilderPrivacy);
        assertEquals(3, privacy.getAdapter().getCount());
        assertEquals("Anonymous", privacy.getAdapter().getItem(0).toString());
        privacy.setSelection(2);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertEquals(2, draft.privacy);
    }

    @Test
    public void errorsShowUntilTheDraftChanges() {
        builder.showError(R.string.fastcomments_poll_error_min_options);
        TextView error = builder.findViewById(R.id.pollBuilderError);
        assertEquals(View.VISIBLE, error.getVisibility());
        assertEquals("Enter at least two options.", error.getText().toString());

        optionInput(0).setText("A");
        assertEquals(View.GONE, error.getVisibility());
    }

    @Test
    public void removeLinkNotifiesTheListener() {
        final List<Boolean> removed = new ArrayList<>();
        builder.setOnRemoveListener(() -> removed.add(true));
        builder.findViewById(R.id.pollBuilderRemove).performClick();
        assertEquals(1, removed.size());
    }

    @Test
    public void inputViewOffersPollsOnlyWhenAllowed() {
        CommentWidgetConfig config = new CommentWidgetConfig("tenant", "url");
        FastCommentsSDK sdk = new FastCommentsSDK(config, true);
        // The input's layout uses AppCompat theme attributes.
        BottomCommentInputView input = new BottomCommentInputView(
                new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_FastComments));
        input.setSDK(sdk);
        assertNull(input.findViewWithTag(BottomCommentInputView.POLL_BUTTON_TAG));
        assertEquals(View.GONE, input.findViewById(R.id.toolbarContainer).getVisibility());

        config.pollCreation = PollCreationMode.NUMBER_2;
        input.refreshPollAvailability();
        View pollButton = input.findViewWithTag(BottomCommentInputView.POLL_BUTTON_TAG);
        assertNotNull(pollButton);
        assertEquals(View.VISIBLE, input.findViewById(R.id.toolbarContainer).getVisibility());
        assertNull(input.getPollDraft());

        pollButton.performClick();
        assertNotNull(input.getPollDraft());
        assertEquals(View.VISIBLE, input.findViewById(R.id.pollBuilderScroll).getVisibility());
        assertTrue(pollButton.isActivated());

        // A half-written poll blocks the post instead of being dropped.
        final List<String> submitted = new ArrayList<>();
        input.setOnCommentSubmitListener((text, parentId) -> submitted.add(text));
        ((EditText) input.findViewById(R.id.commentInput)).setText("hello");
        input.findViewById(R.id.sendButton).performClick();
        assertTrue(submitted.isEmpty());
        assertEquals(
                "Enter a question for your poll.",
                ((TextView) input.findViewById(R.id.pollBuilderError)).getText().toString());

        ((EditText) input.findViewById(R.id.pollBuilderQuestion)).setText("Q");
        ViewGroup options = input.findViewById(R.id.pollBuilderOptions);
        ((EditText) options.getChildAt(0).findViewById(R.id.pollBuilderOptionInput)).setText("A");
        ((EditText) options.getChildAt(1).findViewById(R.id.pollBuilderOptionInput)).setText("B");
        input.findViewById(R.id.sendButton).performClick();
        assertEquals(1, submitted.size());

        input.clearPollDraft();
        assertNull(input.getPollDraft());
        assertEquals(View.GONE, input.findViewById(R.id.pollBuilderScroll).getVisibility());
        assertFalse(pollButton.isActivated());
    }
}
