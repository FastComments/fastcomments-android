package com.fastcomments.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
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
        assertEquals("Lunch?", draft.getQuestion());
        assertEquals("Pizza", draft.getOptions().get(0));
        assertEquals("Tacos", draft.getOptions().get(1));
    }

    @Test
    public void pastedTextIsFlattenedToOneLine() {
        optionInput(0).setText("a\n\tb   c");
        assertEquals("a b c", optionInput(0).getText().toString());
        assertEquals("a b c", draft.getOptions().get(0));
    }

    @Test
    public void addAndRemoveOptionsWithinLimits() {
        optionInput(0).setText("A");
        optionInput(1).setText("B");
        builder.findViewById(R.id.pollBuilderAddOption).performClick();
        assertEquals(3, options().getChildCount());
        assertEquals(3, draft.getOptions().size());
        assertEquals(
                View.VISIBLE,
                optionRow(0).findViewById(R.id.pollBuilderOptionRemove).getVisibility());

        optionRow(0).findViewById(R.id.pollBuilderOptionRemove).performClick();
        assertEquals(2, options().getChildCount());
        assertEquals("B", draft.getOptions().get(0));
        assertEquals("B", optionInput(0).getText().toString());

        for (int i = 0; i < 20; i++) {
            builder.findViewById(R.id.pollBuilderAddOption).performClick();
        }
        assertEquals(PollDraft.MAX_OPTIONS, options().getChildCount());
        assertEquals(View.GONE, builder.findViewById(R.id.pollBuilderAddOption).getVisibility());
    }

    @Test
    public void addingAndRemovingOptionsKeepsTheOtherRows() {
        View first = optionRow(0);
        View second = optionRow(1);
        builder.findViewById(R.id.pollBuilderAddOption).performClick();
        assertSame(first, optionRow(0));
        assertSame(second, optionRow(1));

        first.findViewById(R.id.pollBuilderOptionRemove).performClick();
        assertSame(second, optionRow(0));
        assertEquals(0, ((LinearLayout.LayoutParams) optionRow(0).getLayoutParams()).topMargin);
        assertEquals("Option 1", optionInput(0).getHint().toString());
        assertEquals("Option 2", optionInput(1).getHint().toString());
        assertEquals(
                View.INVISIBLE,
                optionRow(0).findViewById(R.id.pollBuilderOptionRemove).getVisibility());

        // The surviving rows write to their new positions.
        optionInput(0).setText("B");
        optionInput(1).setText("C");
        assertEquals(java.util.Arrays.asList("B", "C"), draft.getOptions());
    }

    @Test
    public void backspaceOnAnEmptyOptionKeepsIt() {
        builder.findViewById(R.id.pollBuilderAddOption).performClick();

        // Removing is left to the × button: a held backspace would go on to empty and remove the options above.
        InputConnection empty = optionInput(2).onCreateInputConnection(new EditorInfo());
        empty.deleteSurroundingText(1, 0);
        optionInput(2).dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
        assertEquals(3, options().getChildCount());
        assertEquals(3, draft.getOptions().size());
    }

    @Test
    public void lateInputOnARemovedRowIsIgnored() {
        builder.findViewById(R.id.pollBuilderAddOption).performClick();
        EditText removed = optionInput(2);
        InputConnection connection = removed.onCreateInputConnection(new EditorInfo());
        optionRow(2).findViewById(R.id.pollBuilderOptionRemove).performClick();
        assertEquals(2, options().getChildCount());

        // The keyboard can still send to the old connection for a moment after the row is gone.
        removed.setText("late");
        removed.setText("");
        connection.deleteSurroundingText(1, 0);
        removed.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
        assertEquals(java.util.Arrays.asList("", ""), draft.getOptions());
    }

    @Test
    public void smallControlsHaveFullTouchTargets() {
        builder.measure(
                View.MeasureSpec.makeMeasureSpec(PollStyle.dp(builder.getContext(), 360), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int min = PollStyle.dp(builder.getContext(), 48);
        View remove = optionRow(0).findViewById(R.id.pollBuilderOptionRemove);
        assertTrue(remove.getMeasuredWidth() >= min);
        assertTrue(remove.getMeasuredHeight() >= min);
        View help = builder.findViewById(R.id.pollBuilderPrivacyHelp);
        assertTrue(help.getMeasuredWidth() >= min);
        assertTrue(help.getMeasuredHeight() >= min);
        ViewGroup durations = builder.findViewById(R.id.pollBuilderDurations);
        for (int i = 0; i < durations.getChildCount(); i++) {
            assertTrue(durations.getChildAt(i).getMeasuredHeight() >= min);
            assertTrue(durations.getChildAt(i).getMeasuredWidth() >= min);
        }
        assertTrue(builder.findViewById(R.id.pollBuilderRemove).getMeasuredHeight() >= min);
    }

    @Test
    public void durationPillsAreExclusive() {
        ViewGroup durations = builder.findViewById(R.id.pollBuilderDurations);
        assertEquals(PollDraft.Duration.values().length, durations.getChildCount());
        assertTrue(durations.getChildAt(0).isSelected());
        assertEquals(
                "No end date", ((TextView) durations.getChildAt(0)).getText().toString());

        durations.getChildAt(2).performClick();
        assertEquals(PollDraft.Duration.THREE_DAYS, draft.getDuration());
        assertFalse(durations.getChildAt(0).isSelected());
        assertTrue(durations.getChildAt(2).isSelected());
    }

    @Test
    public void requireVoteAndPrivacyUpdateTheDraft() {
        CheckBox requireVote = builder.findViewById(R.id.pollBuilderRequireVote);
        assertEquals("Require Vote to See Results", requireVote.getText().toString());
        requireVote.performClick();
        assertTrue(draft.isRequireVoteToSeeResults());

        Spinner privacy = builder.findViewById(R.id.pollBuilderPrivacy);
        assertEquals(3, privacy.getAdapter().getCount());
        assertEquals("Anonymous", privacy.getAdapter().getItem(0).toString());
        privacy.setSelection(2);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertEquals(com.fastcomments.model.PollPrivacy.NUMBER_2, draft.getPrivacy());
    }

    @Test
    public void errorsShowUntilTheDraftChanges() {
        builder.showError(PollDraft.Error.MIN_OPTIONS);
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
        assertNull(input.findViewById(R.id.fastcomments_poll_button));
        assertEquals(View.GONE, input.findViewById(R.id.toolbarContainer).getVisibility());

        config.pollCreation = PollCreationMode.NUMBER_2;
        input.refreshPollAvailability();
        View pollButton = input.findViewById(R.id.fastcomments_poll_button);
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
