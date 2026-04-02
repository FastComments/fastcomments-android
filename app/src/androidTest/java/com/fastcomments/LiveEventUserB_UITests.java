package com.fastcomments;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertTrue;

import android.util.Log;

import com.fastcomments.sdk.R;


import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

/**
 * Actor role — runs on Emulator B.
 * Performs actions in 6 phases that are observed by UserA.
 */
@RunWith(AndroidJUnit4.class)
public class LiveEventUserB_UITests extends UITestBase {

    private static final String TAG = "LiveEventUserB";

    private String urlId;
    private String ssoTokenB;
    private String ssoTokenBAdmin;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Wait for UserA to create the tenant and post setup data
        sync.waitFor("userA", "setup");
        JSONObject config = sync.getData("setup");

        testTenantId = config.getString("tenantId");
        testTenantApiKey = config.getString("apiKey");
        urlId = config.getString("urlId");
        ssoTokenB = config.getString("ssoTokenB");
        ssoTokenBAdmin = config.getString("ssoTokenBAdmin");
    }

    @After
    @Override
    public void tearDown() throws Exception {
        // UserB does not own the tenant — skip tenant deletion
        testTenantEmail = null;
        super.tearDown();
    }

    @Test
    public void testLiveEvents_UserB() throws Exception {
        // --- Phase 1: Post a live comment ---
        Log.d(TAG, "=== Phase 1: Post live comment ===");
        sync.waitFor("userA", "phase1");

        launchActivity(urlId, ssoTokenB);
        pollUntil(15000, () -> {
            try {
                onView(withId(R.id.commentInput)).check(matches(isDisplayed()));
                return true;
            } catch (Exception | AssertionError e) { return false; }
        });

        String commentText = "Live from B " + System.currentTimeMillis();
        onView(withId(R.id.commentInput))
                .perform(click(), typeText(commentText), closeSoftKeyboard());
        onView(withId(R.id.sendButton)).perform(click());

        boolean ownCommentAppeared = false;
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString(commentText)))));
                ownCommentAppeared = true;
                break;
            } catch (Exception | AssertionError e) { Thread.sleep(250); }
        }
        assertTrue("Own comment should appear after posting", ownCommentAppeared);

        JSONObject phase1Data = new JSONObject();
        phase1Data.put("text", commentText);
        sync.postData("phase1", phase1Data);
        sync.signalReady("phase1");
        Log.d(TAG, "Phase 1 complete");

        // --- Phase 2: Vote on UserA's comment ---
        Log.d(TAG, "=== Phase 2: Vote ===");
        sync.waitFor("userA", "phase2");
        JSONObject phase2Data = sync.getData("phase2_setup");
        String voteCommentId = phase2Data.getString("commentId");

        launchActivity(urlId, ssoTokenB);
        pollUntil(15000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Vote target from A")))));
                return true;
            } catch (Exception | AssertionError e) { return false; }
        });

        // Click the upvote button within the "Vote target" item using RecyclerViewActions
        onView(withId(R.id.recyclerViewComments))
                .perform(androidx.test.espresso.contrib.RecyclerViewActions.actionOnItem(
                        hasDescendant(withText(containsString("Vote target from A"))),
                        new androidx.test.espresso.ViewAction() {
                            @Override public org.hamcrest.Matcher<android.view.View> getConstraints() {
                                return org.hamcrest.Matchers.any(android.view.View.class);
                            }
                            @Override public String getDescription() { return "click upVoteButton in item"; }
                            @Override public void perform(androidx.test.espresso.UiController uc, android.view.View v) {
                                v.findViewById(R.id.upVoteButton).performClick();
                            }
                        }));
        sync.signalReady("phase2");
        Log.d(TAG, "Phase 2 complete");

        // --- Phase 3: Presence ---
        Log.d(TAG, "=== Phase 3: Presence ===");
        sync.waitFor("userA", "phase3");

        launchActivity(urlId, ssoTokenB);
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.commentInput)).check(matches(isDisplayed()));
                return true;
            } catch (Exception | AssertionError e) { return false; }
        });

        sync.signalReady("phase3");
        Log.d(TAG, "Phase 3 complete");

        // --- Phase 4: Seed a comment then delete it ---
        Log.d(TAG, "=== Phase 4: Delete ===");
        sync.waitFor("userA", "phase4_ready");

        String deleteText = "Delete me live " + System.currentTimeMillis();
        String deleteCommentId = seedComment(urlId, deleteText, ssoTokenB);
        Log.d(TAG, "Phase 4: seeded comment " + deleteCommentId + " text=" + deleteText);

        JSONObject phase4Data = new JSONObject();
        phase4Data.put("text", deleteText);
        sync.postData("phase4_posted", phase4Data);
        sync.signalReady("phase4_posted");

        sync.waitFor("userA", "phase4_seen");

        // Delete via UI menu — relaunch, find the comment, tap menu > delete > confirm
        launchActivity(urlId, ssoTokenB);
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString(deleteText)))));
                return true;
            } catch (Exception | AssertionError e) { return false; }
        });

        // Click menu on the specific comment using RecyclerViewActions
        onView(withId(R.id.recyclerViewComments))
                .perform(androidx.test.espresso.contrib.RecyclerViewActions.actionOnItem(
                        hasDescendant(withText(containsString(deleteText))),
                        new androidx.test.espresso.ViewAction() {
                            @Override public org.hamcrest.Matcher<android.view.View> getConstraints() { return org.hamcrest.Matchers.any(android.view.View.class); }
                            @Override public String getDescription() { return "click menu in item"; }
                            @Override public void perform(androidx.test.espresso.UiController uc, android.view.View v) {
                                v.findViewById(R.id.commentMenuButton).performClick();
                            }
                        }));
        onView(withText(R.string.delete_comment)).perform(click());

        // Confirm in AlertDialog
        onView(withText(R.string.delete))
                .inRoot(isDialog())
                .perform(click());

        // Wait for comment to disappear from UserB's own view
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString(deleteText)))));
                return false; // still there
            } catch (Exception | AssertionError e) {
                return true; // gone
            }
        });

        sync.signalReady("phase4_deleted");
        Log.d(TAG, "Phase 4 complete");

        // --- Phase 5: Pin via SDK pin endpoint ---
        Log.d(TAG, "=== Phase 5: Pin ===");
        sync.waitFor("userA", "phase5");
        JSONObject phase5Data = sync.getData("phase5_setup");
        String pinCommentId = phase5Data.getString("commentId");

        // Load comments with admin SSO to register the admin user first
        launchActivity(urlId, ssoTokenBAdmin);
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments)).check(matches(isDisplayed()));
                return true;
            } catch (Exception | AssertionError e) { return false; }
        });

        pinComment(pinCommentId, ssoTokenBAdmin);

        sync.signalReady("phase5");
        Log.d(TAG, "Phase 5 complete");

        // --- Phase 6: Lock via SDK lock endpoint ---
        Log.d(TAG, "=== Phase 6: Lock ===");
        sync.waitFor("userA", "phase6");
        JSONObject phase6Data = sync.getData("phase6_setup");
        String lockCommentId = phase6Data.getString("commentId");

        lockComment(lockCommentId, ssoTokenBAdmin);

        sync.signalReady("phase6");
        Log.d(TAG, "Phase 6 complete");

        sync.waitFor("userA", "done");
    }
}
