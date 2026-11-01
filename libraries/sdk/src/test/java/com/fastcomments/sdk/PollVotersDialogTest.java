package com.fastcomments.sdk;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.view.View;
import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.APIError;
import com.fastcomments.model.GetPollVoterNamesSuccessResponse;
import com.fastcomments.model.PollVoterEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PollVotersDialogTest {

    private final List<Integer> pagesRequested = new ArrayList<>();
    private final List<FCCallback<GetPollVoterNamesSuccessResponse>> pending = new ArrayList<>();
    private PollVotersDialog dialog;

    @Before
    public void setup() {
        FastCommentsSDK sdk = new FastCommentsSDK(new CommentWidgetConfig("tenant", "url"), true) {
            @Override
            public void getPollVoters(
                    String commentId, int page, String search, FCCallback<GetPollVoterNamesSuccessResponse> callback) {
                pagesRequested.add(page);
                pending.add(callback);
            }
        };
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_FastComments);
        dialog = new PollVotersDialog(activity, sdk, "comment");
        dialog.show();
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

    private View view(int id) {
        return dialog.findViewById(id);
    }

    @Test
    public void pagerTapsWaitForTheCurrentPage() {
        pending.get(0).onSuccess(page(0, true));

        view(R.id.pollVotersNext).performClick();
        view(R.id.pollVotersNext).performClick();
        view(R.id.pollVotersNext).performClick();
        assertEquals(Arrays.asList(0, 1), pagesRequested);

        pending.get(1).onSuccess(page(1, false));
        view(R.id.pollVotersPrev).performClick();
        assertEquals(Arrays.asList(0, 1, 0), pagesRequested);
    }

    @Test
    public void failedPageCanBeRetried() {
        pending.get(0).onSuccess(page(0, true));
        view(R.id.pollVotersNext).performClick();
        pending.get(1).onFailure(new APIError().code("internal"));

        view(R.id.pollVotersNext).performClick();
        assertEquals(Arrays.asList(0, 1, 1), pagesRequested);
    }
}
