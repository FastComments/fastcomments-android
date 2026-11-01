package com.fastcomments.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.PollCreationMode;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PollDraftStateTest {

    private Activity activity;
    private FastCommentsSDK sdk;

    @Before
    public void setup() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_FastComments);
        CommentWidgetConfig config = new CommentWidgetConfig("tenant", "url");
        config.pollCreation = PollCreationMode.NUMBER_2;
        sdk = new FastCommentsSDK(config, true);
    }

    private BottomCommentInputView createInput() {
        BottomCommentInputView input = new BottomCommentInputView(activity);
        input.setId(R.id.bottomCommentInput);
        input.setSDK(sdk);
        return input;
    }

    private static EditText optionInput(BottomCommentInputView input, int index) {
        ViewGroup options = input.findViewById(R.id.pollBuilderOptions);
        return options.getChildAt(index).findViewById(R.id.pollBuilderOptionInput);
    }

    // Writes the saved state through a Parcel, as the system does across process death.
    private static SparseArray<Parcelable> roundTrip(SparseArray<Parcelable> state) {
        Bundle bundle = new Bundle();
        bundle.putSparseParcelableArray("views", state);
        Parcel parcel = Parcel.obtain();
        parcel.writeBundle(bundle);
        parcel.setDataPosition(0);
        Bundle restored = parcel.readBundle(PollDraftStateTest.class.getClassLoader());
        parcel.recycle();
        return restored.getSparseParcelableArray("views");
    }

    @Test
    public void draftSurvivesRecreation() {
        BottomCommentInputView input = createInput();
        input.findViewWithTag(BottomCommentInputView.POLL_BUTTON_TAG).performClick();
        ((EditText) input.findViewById(R.id.pollBuilderQuestion)).setText("Lunch?");
        optionInput(input, 0).setText("Pizza");
        optionInput(input, 1).setText("Tacos");
        input.findViewById(R.id.pollBuilderAddOption).performClick();
        optionInput(input, 2).setText("Soup");
        input.getPollDraft().closesDays = 3;
        input.getPollDraft().privacy = 2;
        input.getPollDraft().requireVoteToSeeResults = true;

        SparseArray<Parcelable> state = new SparseArray<>();
        input.saveHierarchyState(state);

        BottomCommentInputView restored = createInput();
        restored.restoreHierarchyState(roundTrip(state));

        PollDraft draft = restored.getPollDraft();
        assertNotNull(draft);
        assertEquals("Lunch?", draft.question);
        assertEquals(java.util.Arrays.asList("Pizza", "Tacos", "Soup"), draft.options);
        assertEquals(3, draft.closesDays);
        assertEquals(2, draft.privacy);
        assertTrue(draft.requireVoteToSeeResults);
        assertEquals(View.VISIBLE, restored.findViewById(R.id.pollBuilderScroll).getVisibility());
        assertEquals("Pizza", optionInput(restored, 0).getText().toString());
        assertEquals("Tacos", optionInput(restored, 1).getText().toString());
        assertEquals("Soup", optionInput(restored, 2).getText().toString());
        assertEquals(
                "Lunch?",
                ((EditText) restored.findViewById(R.id.pollBuilderQuestion))
                        .getText()
                        .toString());
    }

    @Test
    public void restoredDraftWaitsForTheServerConfig() {
        BottomCommentInputView input = createInput();
        input.findViewWithTag(BottomCommentInputView.POLL_BUTTON_TAG).performClick();
        ((EditText) input.findViewById(R.id.pollBuilderQuestion)).setText("Lunch?");
        SparseArray<Parcelable> state = new SparseArray<>();
        input.saveHierarchyState(state);

        // Before load the recreated SDK does not know the tenant allows polls yet.
        sdk = new FastCommentsSDK(new CommentWidgetConfig("tenant", "url"), true);
        BottomCommentInputView restored = createInput();
        restored.restoreHierarchyState(roundTrip(state));
        restored.setCurrentUser(null);

        assertNotNull(restored.getPollDraft());
        assertEquals("Lunch?", restored.getPollDraft().question);
    }
}
