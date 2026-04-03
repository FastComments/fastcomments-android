package com.fastcomments;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.fastcomments.sdk.R;

import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Single-emulator tests for comment ordering.
 */
@RunWith(AndroidJUnit4.class)
public class PaginationUITests extends UITestBase {

    private String urlId;
    private String ssoToken;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        createTestTenant("android-pagination@fctest.com");
        ssoToken = makeSecureSSOToken("pagination-user");
    }

    @Test
    public void testCommentsRenderedInOrder() throws Exception {
        urlId = "order-" + System.currentTimeMillis();

        // Seed 3 comments in order — newest-first sort means Third should be on top
        seedComment(urlId, "First comment", ssoToken);
        Thread.sleep(100); // ensure distinct timestamps
        seedComment(urlId, "Second comment", ssoToken);
        Thread.sleep(100);
        seedComment(urlId, "Third comment", ssoToken);

        launchActivity(urlId, ssoToken);

        // Wait for all comments to appear
        pollUntil(15000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Third comment")))));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        // Verify all three are present
        onView(withId(R.id.recyclerViewComments))
                .check(matches(hasDescendant(withText(containsString("First comment")))));
        onView(withId(R.id.recyclerViewComments))
                .check(matches(hasDescendant(withText(containsString("Second comment")))));

        // Verify newest-first order: check that the first item in the RecyclerView
        // contains "Third comment" (the newest)
        final boolean[] orderCorrect = {false};
        onView(withId(R.id.recyclerViewComments)).perform(new ViewAction() {
            @Override
            public Matcher<View> getConstraints() {
                return org.hamcrest.Matchers.any(View.class);
            }

            @Override
            public String getDescription() {
                return "Check first item text";
            }

            @Override
            public void perform(UiController uiController, View view) {
                RecyclerView rv = (RecyclerView) view;
                RecyclerView.ViewHolder holder = rv.findViewHolderForAdapterPosition(0);
                if (holder != null) {
                    TextView content = holder.itemView.findViewById(R.id.commentContent);
                    if (content != null) {
                        String text = content.getText().toString();
                        orderCorrect[0] = text.contains("Third comment");
                    }
                }
            }
        });

        assertTrue("Newest comment (Third) should be at position 0 (newest-first)", orderCorrect[0]);
    }
}
