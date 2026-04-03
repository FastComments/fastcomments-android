package com.fastcomments;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;

import android.util.Log;
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
 * Single-emulator tests for live chat pagination.
 * Seeds more messages than one page (30) can hold, then verifies
 * that older messages load when the user scrolls up or taps
 * the "load older" button.
 */
@RunWith(AndroidJUnit4.class)
public class LiveChatPaginationUITests extends UITestBase {

    private static final String TAG = "LiveChatPagination";
    private static final int TOTAL_MESSAGES = 50;

    private String urlId;
    private String ssoToken;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        createTestTenant("android-chat-pagination@fctest.com");
        ssoToken = makeSecureSSOToken("chat-page-user");
    }

    @Test
    public void testLoadOlderMessages() throws Exception {
        urlId = "chat-page-" + System.currentTimeMillis();

        // Seed messages numbered 1..35 so we can identify them by text.
        // With oldest-first sort and page size 30, the initial load returns
        // messages 6..35 (the 30 newest). Messages 1..5 are on the next page.
        Log.d(TAG, "Seeding " + TOTAL_MESSAGES + " messages...");
        for (int i = 1; i <= TOTAL_MESSAGES; i++) {
            seedComment(urlId, "msg-" + i, ssoToken);
        }

        launchLiveChatActivity(urlId, ssoToken);
        Thread.sleep(3000);

        // Verify a recent message is visible
        pollUntil(15000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("msg-" + TOTAL_MESSAGES)))));
                return true;
            } catch (Exception | AssertionError e) { return false; }
        });
        Log.d(TAG, "Latest message visible");

        // Check that not all messages loaded — pagination should be needed
        final int[] adapterCount = {0};
        onView(withId(R.id.recyclerViewComments)).perform(new ViewAction() {
            @Override public Matcher<View> getConstraints() { return org.hamcrest.Matchers.any(View.class); }
            @Override public String getDescription() { return "read adapter count"; }
            @Override public void perform(UiController uc, View v) {
                RecyclerView rv = (RecyclerView) v;
                adapterCount[0] = rv.getAdapter() != null ? rv.getAdapter().getItemCount() : 0;
            }
        });
        Log.d(TAG, "Initial adapter count: " + adapterCount[0]);
        assertTrue("Should have fewer items than total seeded (pagination needed)",
                adapterCount[0] < TOTAL_MESSAGES);

        int countBefore = adapterCount[0];

        // Pagination buttons should NOT be visible — live chat uses infinite scroll
        onView(withId(R.id.paginationControls)).check(matches(not(isDisplayed())));
        Log.d(TAG, "Pagination buttons correctly hidden");

        // Scroll to the top to trigger infinite scroll loading of older messages
        onView(withId(R.id.recyclerViewComments)).perform(
                androidx.test.espresso.action.ViewActions.swipeDown());
        onView(withId(R.id.recyclerViewComments)).perform(
                androidx.test.espresso.action.ViewActions.swipeDown());
        onView(withId(R.id.recyclerViewComments)).perform(
                androidx.test.espresso.action.ViewActions.swipeDown());
        Log.d(TAG, "Swiped up to trigger infinite scroll");

        // Poll for adapter count to increase
        final int[] countAfter = {0};
        boolean moreLoaded = false;
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            onView(withId(R.id.recyclerViewComments)).perform(new ViewAction() {
                @Override public Matcher<View> getConstraints() { return org.hamcrest.Matchers.any(View.class); }
                @Override public String getDescription() { return "read adapter count after scroll"; }
                @Override public void perform(UiController uc, View v) {
                    RecyclerView rv = (RecyclerView) v;
                    countAfter[0] = rv.getAdapter() != null ? rv.getAdapter().getItemCount() : 0;
                }
            });
            if (countAfter[0] > countBefore) {
                moreLoaded = true;
                break;
            }
            Thread.sleep(250);
        }
        Log.d(TAG, "After scroll: " + countBefore + " -> " + countAfter[0]);
        assertTrue("Infinite scroll should load more items when scrolling up", moreLoaded);
    }

    @Test
    public void testMessagesInOldestFirstOrder() throws Exception {
        urlId = "chat-order-" + System.currentTimeMillis();

        // Seed 3 messages
        seedComment(urlId, "First message", ssoToken);
        Thread.sleep(100);
        seedComment(urlId, "Second message", ssoToken);
        Thread.sleep(100);
        seedComment(urlId, "Third message", ssoToken);

        launchLiveChatActivity(urlId, ssoToken);
        Thread.sleep(3000);

        // Wait for any message to load
        pollUntil(15000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("message")))));
                return true;
            } catch (Exception | AssertionError e) { return false; }
        });

        // In live chat (oldest-first), position 0 should be the oldest message.
        // Position 0 may be a non-comment node (e.g. pagination button), so find
        // the first comment position by scanning from 0.
        final String[] firstCommentText = {""};
        final String[] lastCommentText = {""};
        onView(withId(R.id.recyclerViewComments)).perform(new ViewAction() {
            @Override public Matcher<View> getConstraints() { return org.hamcrest.Matchers.any(View.class); }
            @Override public String getDescription() { return "Read first and last comment text"; }
            @Override public void perform(UiController uiController, View view) {
                RecyclerView rv = (RecyclerView) view;
                int count = rv.getAdapter() != null ? rv.getAdapter().getItemCount() : 0;
                // Find first comment
                for (int i = 0; i < count; i++) {
                    RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(i);
                    if (h != null) {
                        TextView t = h.itemView.findViewById(R.id.commentContent);
                        if (t != null && !t.getText().toString().isEmpty()) {
                            firstCommentText[0] = t.getText().toString();
                            break;
                        }
                    }
                }
                // Find last comment
                for (int i = count - 1; i >= 0; i--) {
                    RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(i);
                    if (h != null) {
                        TextView t = h.itemView.findViewById(R.id.commentContent);
                        if (t != null && !t.getText().toString().isEmpty()) {
                            lastCommentText[0] = t.getText().toString();
                            break;
                        }
                    }
                }
            }
        });
        Log.d(TAG, "First comment: " + firstCommentText[0] + ", Last comment: " + lastCommentText[0]);

        assertTrue("First comment should be 'First message' (oldest-first chat order)",
                firstCommentText[0].contains("First message"));
        assertTrue("Last comment should be 'Third message' (oldest-first chat order)",
                lastCommentText[0].contains("Third message"));
    }
}
