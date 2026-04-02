package com.fastcomments;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertTrue;

import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.fastcomments.sdk.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Single-emulator tests for comment CRUD operations.
 */
@RunWith(AndroidJUnit4.class)
public class CommentCRUDUITests extends UITestBase {

    private static final String TAG = "CommentCRUDUITests";
    private String urlId;
    private String ssoToken;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        createTestTenant();
        ssoToken = makeSecureSSOToken("crud-user");
    }

    @Test
    public void testEmptyPageShowsEmptyState() throws Exception {
        urlId = "crud-empty-" + System.currentTimeMillis();
        launchActivity(urlId, ssoToken);

        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.emptyStateView)).check(matches(isDisplayed()));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        onView(withId(R.id.emptyStateView))
                .check(matches(isDisplayed()))
                .check(matches(withText(containsString("comment"))));
    }

    @Test
    public void testTypeAndSubmitComment() throws Exception {
        urlId = "crud-submit-" + System.currentTimeMillis();
        launchActivity(urlId, ssoToken);

        pollUntil(15000, () -> {
            try {
                onView(withId(R.id.commentInput)).check(matches(isDisplayed()));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        onView(withId(R.id.commentInput))
                .perform(click(), typeText("Hello from UI test"), closeSoftKeyboard());
        onView(withId(R.id.sendButton))
                .perform(click());

        boolean found = false;
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Hello from UI test")))));
                found = true;
                break;
            } catch (Exception | AssertionError e) {
                Thread.sleep(250);
            }
        }
        assertTrue("Comment should appear after posting", found);
    }

    @Test
    public void testEditCommentViaMenu() throws Exception {
        urlId = "crud-edit-" + System.currentTimeMillis();
        launchActivity(urlId, ssoToken);

        // Post a comment
        pollUntil(15000, () -> {
            try {
                onView(withId(R.id.commentInput)).check(matches(isDisplayed()));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        onView(withId(R.id.commentInput))
                .perform(click(), typeText("Original text"), closeSoftKeyboard());
        onView(withId(R.id.sendButton)).perform(click());

        // Wait for comment to appear
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Original text")))));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        // Tap menu and edit
        onView(withId(R.id.commentMenuButton)).perform(click());
        onView(withText(R.string.edit_comment)).perform(click());

        // Edit in dialog
        onView(withId(R.id.editCommentText))
                .inRoot(isDialog())
                .perform(clearText(), typeText("Edited text"), closeSoftKeyboard());
        onView(withId(R.id.saveEditButton))
                .inRoot(isDialog())
                .perform(click());

        // Verify edited text appears
        boolean found = false;
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Edited text")))));
                found = true;
                break;
            } catch (Exception | AssertionError e) {
                Thread.sleep(250);
            }
        }
        assertTrue("Edited text should appear", found);
    }

    @Test
    public void testDeleteCommentViaMenu() throws Exception {
        urlId = "crud-delete-" + System.currentTimeMillis();
        launchActivity(urlId, ssoToken);

        // Post a comment
        pollUntil(15000, () -> {
            try {
                onView(withId(R.id.commentInput)).check(matches(isDisplayed()));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        onView(withId(R.id.commentInput))
                .perform(click(), typeText("Delete me"), closeSoftKeyboard());
        onView(withId(R.id.sendButton)).perform(click());

        // Wait for comment to appear
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Delete me")))));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        // Tap menu and delete
        onView(withId(R.id.commentMenuButton)).perform(click());
        onView(withText(R.string.delete_comment)).perform(click());

        // Confirm in AlertDialog — positive button is R.string.delete ("Delete"), not R.string.delete_comment
        onView(withText(R.string.delete))
                .inRoot(isDialog())
                .perform(click());
        Thread.sleep(1000); // Let dialog dismiss and delete API complete

        // Verify comment disappeared — check that the empty state reappears
        // (since this was the only comment, deleting it shows the empty state)
        boolean disappeared = false;
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.emptyStateView)).check(matches(isDisplayed()));
                disappeared = true;
                break;
            } catch (Exception | AssertionError e) {
                Thread.sleep(250);
            }
        }
        assertTrue("Comment should disappear after deletion (empty state should show)", disappeared);
    }

    @Test
    public void testPaginationLoadsMore() throws Exception {
        urlId = "crud-pagination-" + System.currentTimeMillis();
        Log.d(TAG, "Seeding 35 comments...");

        // Seed 35 comments sequentially to preserve ordering
        for (int i = 1; i <= 35; i++) {
            seedComment(urlId, "Comment " + i, ssoToken);
        }
        Log.d(TAG, "Comments seeded, launching activity");

        launchActivity(urlId, ssoToken);

        // Wait for the newest comment to appear (Comment 35)
        pollUntil(15000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Comment 35")))));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        // Wait for pagination controls to appear (they're below the RecyclerView)
        boolean paginationFound = false;
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.btnNextComments)).check(matches(isDisplayed()));
                paginationFound = true;
                break;
            } catch (Exception | AssertionError e) {
                Thread.sleep(250);
            }
        }
        assertTrue("Pagination button should appear", paginationFound);

        // Tap next page
        Log.d(TAG, "Clicking pagination next button");
        onView(withId(R.id.btnNextComments)).perform(click());

        // Wait for page 2 to load. After loading, the adapter should have all 35 items.
        // Check by scrolling to the last position and looking for "Comment 1".
        boolean found = false;
        deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                // Try scrolling to the last adapter position to find older comments
                onView(withId(R.id.recyclerViewComments))
                        .perform(androidx.test.espresso.contrib.RecyclerViewActions
                                .scrollToPosition(34)); // 0-indexed, position 34 = 35th item
                Thread.sleep(500);
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Comment 1")))));
                found = true;
                break;
            } catch (Exception | AssertionError e) {
                Log.d(TAG, "Pagination: Comment 1 not found yet, retrying...");
                Thread.sleep(500);
            }
        }
        assertTrue("Earlier comments should appear after pagination", found);
    }
}
