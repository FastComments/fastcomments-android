package com.fastcomments.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Looper;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.CommentPoll;
import com.fastcomments.model.GetPollVoterNamesSuccessResponse;
import com.fastcomments.model.PollPrivacy;
import com.fastcomments.model.PollVoterEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
public class PollVotersDialogFragmentTest {

    private static FastCommentsSDK hostSdk;

    public static class HostActivity extends FragmentActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            setTheme(R.style.Theme_FastComments);
            super.onCreate(savedInstanceState);
            setContentView(new FastCommentsView(this, hostSdk));
        }
    }

    private final List<Integer> pagesRequested = new ArrayList<>();
    private final List<String> searchesRequested = new ArrayList<>();
    private final List<FCCallback<GetPollVoterNamesSuccessResponse>> pending = new ArrayList<>();

    private FastCommentsSDK createSdk() {
        return new FastCommentsSDK(new CommentWidgetConfig("tenant", "url"), true) {
            @Override
            public void getPollVoters(
                    String commentId, int page, String search, FCCallback<GetPollVoterNamesSuccessResponse> callback) {
                pagesRequested.add(page);
                searchesRequested.add(search);
                pending.add(callback);
            }
        };
    }

    private static GetPollVoterNamesSuccessResponse page(int page, boolean hasMore) {
        return new GetPollVoterNamesSuccessResponse()
                .voters(Collections.singletonList(
                        new PollVoterEntry().name("Ann").optionLabel("Tacos")))
                .page(page)
                .pageSize(25)
                .total(30)
                .hasMore(hasMore);
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static Dialog shownDialog(FragmentActivity activity) {
        Fragment fragment = activity.getSupportFragmentManager().findFragmentByTag(PollVotersDialogFragment.TAG);
        assertTrue(fragment instanceof PollVotersDialogFragment);
        Dialog dialog = ((PollVotersDialogFragment) fragment).getDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        return dialog;
    }

    @Test
    public void rotationKeepsTheDialogOnItsPage() {
        hostSdk = createSdk();
        ActivityController<HostActivity> controller =
                Robolectric.buildActivity(HostActivity.class).setup();
        PollVotersDialogFragment.show(controller.get(), hostSdk, "comment");
        idle();
        pending.get(0).onSuccess(page(0, true));
        shownDialog(controller.get()).findViewById(R.id.pollVotersNext).performClick();
        pending.get(1).onSuccess(page(1, false));

        // The recreated activity builds its own SDK, which the dialog must pick up.
        hostSdk = createSdk();
        controller.recreate();
        idle();

        shownDialog(controller.get());
        assertEquals(Arrays.asList(0, 1, 1), pagesRequested);
    }

    @Test
    public void rotationKeepsTheSearch() {
        hostSdk = createSdk();
        ActivityController<HostActivity> controller =
                Robolectric.buildActivity(HostActivity.class).setup();
        PollVotersDialogFragment.show(controller.get(), hostSdk, "comment");
        idle();
        pending.get(0).onSuccess(page(0, true));
        EditText search = shownDialog(controller.get()).findViewById(R.id.pollVotersSearch);
        search.setText("Ann");
        search.onEditorAction(EditorInfo.IME_ACTION_SEARCH);

        hostSdk = createSdk();
        controller.recreate();
        idle();

        EditText restored = shownDialog(controller.get()).findViewById(R.id.pollVotersSearch);
        assertEquals("Ann", restored.getText().toString());
        assertEquals(Arrays.asList("", "Ann", "Ann"), searchesRequested);
        assertEquals(Arrays.asList(0, 0, 0), pagesRequested);
    }

    @Test
    public void plainActivityDialogClosesWithTheActivity() {
        ActivityController<Activity> controller =
                Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        activity.setTheme(R.style.Theme_FastComments);
        FrameLayout root = new FrameLayout(activity);
        activity.setContentView(root);
        PollCardView card = new PollCardView(activity);
        root.addView(card);
        CommentPoll poll = PollHelperTest.makePoll(1, 2).privacy(PollPrivacy.NUMBER_2);
        card.bind(createSdk(), new RenderableComment(PollHelperTest.makePollComment(poll)));

        card.findViewById(R.id.pollVoters).performClick();
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertTrue(dialog instanceof PollVotersDialog);
        assertTrue(dialog.isShowing());

        // What the system does when the activity is destroyed.
        activity.getWindowManager().removeViewImmediate(activity.getWindow().getDecorView());
        assertFalse(dialog.isShowing());
    }
}
