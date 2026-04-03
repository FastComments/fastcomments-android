package com.fastcomments;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.util.Log;

import com.fastcomments.sdk.R;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

/**
 * Observer role — runs on Emulator A.
 * Tests 6 phases of live event behavior via WebSocket.
 */
@RunWith(AndroidJUnit4.class)
public class LiveEventUserA_UITests extends UITestBase {

    private static final String TAG = "LiveEventUserA";

    private String urlId;
    private String ssoTokenA;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        // UserA creates the test tenant
        createTestTenant("android-live-events@fctest.com");

        urlId = "live-" + System.currentTimeMillis();
        ssoTokenA = makeSecureSSOToken("userA-live");
        String ssoTokenB = makeSecureSSOToken("userB-live");
        String ssoTokenBAdmin = makeSecureSSOToken("userB-live", true);

        // Post setup data for UserB
        JSONObject setupData = new JSONObject();
        setupData.put("tenantId", testTenantId);
        setupData.put("apiKey", testTenantApiKey);
        setupData.put("urlId", urlId);
        setupData.put("ssoTokenB", ssoTokenB);
        setupData.put("ssoTokenBAdmin", ssoTokenBAdmin);
        sync.postData("setup", setupData);
        sync.signalReady("setup");
    }

    @Test
    public void testLiveEvents_UserA() throws Exception {
        // Launch and wait for the widget to load
        launchActivity(urlId, ssoTokenA);
        Log.d(TAG, "Activity launched, waiting for RecyclerView...");

        pollUntil(15000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments)).check(matches(isDisplayed()));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        // --- Phase 1: Live comment ---
        Log.d(TAG, "=== Phase 1: Live comment ===");
        sync.signalReady("phase1");
        sync.waitFor("userB", "phase1");

        JSONObject phase1Data = sync.getData("phase1");
        String commentText = phase1Data.getString("text");
        Log.d(TAG, "Looking for live comment: " + commentText);

        boolean found = false;
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString(commentText)))));
                found = true;
                break;
            } catch (Exception | AssertionError e) { Thread.sleep(250); }
        }
        Log.d(TAG, "Phase 1 result: " + found);
        assertTrue("Live comment should appear via WebSocket", found);

        // --- Phase 2: Live vote ---
        Log.d(TAG, "=== Phase 2: Live vote ===");
        String voteCommentId = seedComment(urlId, "Vote target from A", ssoTokenA);
        if (voteCommentId == null) {
            voteCommentId = fetchLatestCommentId(urlId);
        }
        assertNotNull("Should have vote target comment ID", voteCommentId);

        JSONObject phase2Setup = new JSONObject();
        phase2Setup.put("commentId", voteCommentId);
        sync.postData("phase2_setup", phase2Setup);

        // Relaunch FIRST so WS is connected before UserB votes
        launchActivity(urlId, ssoTokenA);
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Vote target from A")))));
                return true;
            } catch (Exception | AssertionError e) { return false; }
        });

        // NOW signal UserB to vote (WS should be connected by now)
        sync.signalReady("phase2");
        sync.waitFor("userB", "phase2");

        // Poll for vote count to change on the "Vote target" comment
        final boolean[] voteChanged = {false};
        deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .perform(androidx.test.espresso.contrib.RecyclerViewActions.actionOnItem(
                                hasDescendant(withText(containsString("Vote target from A"))),
                                new androidx.test.espresso.ViewAction() {
                                    @Override public org.hamcrest.Matcher<android.view.View> getConstraints() {
                                        return org.hamcrest.Matchers.any(android.view.View.class);
                                    }
                                    @Override public String getDescription() { return "check vote count"; }
                                    @Override public void perform(androidx.test.espresso.UiController uc, android.view.View v) {
                                        android.widget.TextView voteCount = v.findViewById(R.id.upVoteCount);
                                        if (voteCount != null && !"0".equals(voteCount.getText().toString())) {
                                            voteChanged[0] = true;
                                        }
                                    }
                                }));
                if (voteChanged[0]) break;
            } catch (Exception | AssertionError e) { /* retry */ }
            Thread.sleep(250);
        }
        Log.d(TAG, "Phase 2 result: " + voteChanged[0]);
        assertTrue("Vote count should change after UserB votes", voteChanged[0]);

        // --- Phase 3: Presence (three-user test) ---
        Log.d(TAG, "=== Phase 3: Presence ===");

        // Seed a comment from UserC who will NOT be connected via WebSocket.
        // This lets us verify the correct indicators light up (UserB = online,
        // UserC = offline) rather than just "any indicator is visible".
        String ssoTokenC = makeSecureSSOToken("userC-live");
        seedComment(urlId, "Offline user C comment", ssoTokenC);

        // Relaunch so the RecyclerView includes UserC's comment and WS reconnects
        launchActivity(urlId, ssoTokenA);
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Offline user C comment")))));
                return true;
            } catch (Exception | AssertionError e) { return false; }
        });

        // Signal UserB to join (UserA's WS should be connected by now)
        sync.signalReady("phase3");
        sync.waitFor("userB", "phase3");

        // Poll until UserB's comment shows the online indicator
        final boolean[] userBOnline = {false};
        deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .perform(androidx.test.espresso.contrib.RecyclerViewActions.actionOnItem(
                                hasDescendant(withText(containsString(commentText))),
                                new androidx.test.espresso.ViewAction() {
                                    @Override public org.hamcrest.Matcher<android.view.View> getConstraints() {
                                        return org.hamcrest.Matchers.any(android.view.View.class);
                                    }
                                    @Override public String getDescription() { return "check online indicator on UserB comment"; }
                                    @Override public void perform(androidx.test.espresso.UiController uc, android.view.View v) {
                                        android.view.View indicator = v.findViewById(R.id.onlineIndicator);
                                        if (indicator != null && indicator.getVisibility() == android.view.View.VISIBLE) {
                                            userBOnline[0] = true;
                                        }
                                    }
                                }));
                if (userBOnline[0]) break;
            } catch (Exception | AssertionError e) { /* retry */ }
            Thread.sleep(250);
        }
        Log.d(TAG, "Phase 3 UserB online: " + userBOnline[0]);
        assertTrue("UserB's comment should show online indicator", userBOnline[0]);

        // Verify UserC's comment does NOT show the online indicator
        final boolean[] userCIndicatorOff = {false};
        onView(withId(R.id.recyclerViewComments))
                .perform(androidx.test.espresso.contrib.RecyclerViewActions.actionOnItem(
                        hasDescendant(withText(containsString("Offline user C comment"))),
                        new androidx.test.espresso.ViewAction() {
                            @Override public org.hamcrest.Matcher<android.view.View> getConstraints() {
                                return org.hamcrest.Matchers.any(android.view.View.class);
                            }
                            @Override public String getDescription() { return "check online indicator on UserC comment"; }
                            @Override public void perform(androidx.test.espresso.UiController uc, android.view.View v) {
                                android.view.View indicator = v.findViewById(R.id.onlineIndicator);
                                userCIndicatorOff[0] = indicator == null || indicator.getVisibility() != android.view.View.VISIBLE;
                            }
                        }));
        Log.d(TAG, "Phase 3 UserC offline: " + userCIndicatorOff[0]);
        assertTrue("Offline UserC's comment should NOT show online indicator", userCIndicatorOff[0]);

        // --- Phase 4: Live delete ---
        Log.d(TAG, "=== Phase 4: Live delete ===");
        sync.signalReady("phase4_ready");
        sync.waitFor("userB", "phase4_posted");

        JSONObject phase4Data = sync.getData("phase4_posted");
        String deleteText = phase4Data.getString("text");

        // Relaunch to see the comment
        launchActivity(urlId, ssoTokenA);
        boolean deleteVisible = false;
        deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString(deleteText)))));
                deleteVisible = true;
                break;
            } catch (Exception | AssertionError e) { Thread.sleep(250); }
        }
        assertTrue("Comment to delete should be visible", deleteVisible);

        sync.signalReady("phase4_seen");
        sync.waitFor("userB", "phase4_deleted");

        // Poll for live delete event — no relaunch fallback, must arrive via WebSocket
        boolean disappeared = false;
        deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString(deleteText)))));
                Thread.sleep(250);
            } catch (Exception | AssertionError e) {
                disappeared = true;
                break;
            }
        }
        Log.d(TAG, "Phase 4 result: " + disappeared);
        assertTrue("Deleted comment should disappear via live WebSocket event", disappeared);

        // --- Phase 5: Live pin ---
        Log.d(TAG, "=== Phase 5: Live pin ===");
        String pinCommentId = seedComment(urlId, "Pin target from A", ssoTokenA);
        if (pinCommentId == null) {
            pinCommentId = fetchLatestCommentId(urlId);
        }
        Log.d(TAG, "Phase 5: pin target ID=" + pinCommentId);
        assertNotNull("Should have pin target comment ID", pinCommentId);

        launchActivity(urlId, ssoTokenA);
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Pin target from A")))));
                return true;
            } catch (Exception | AssertionError e) { return false; }
        });

        JSONObject phase5Setup = new JSONObject();
        phase5Setup.put("commentId", pinCommentId);
        sync.postData("phase5_setup", phase5Setup);
        sync.signalReady("phase5");
        sync.waitFor("userB", "phase5");

        // Check pin icon within the "Pin target" item specifically
        final boolean[] pinVisible = {false};
        deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .perform(androidx.test.espresso.contrib.RecyclerViewActions.actionOnItem(
                                hasDescendant(withText(containsString("Pin target from A"))),
                                new androidx.test.espresso.ViewAction() {
                                    @Override public org.hamcrest.Matcher<android.view.View> getConstraints() { return org.hamcrest.Matchers.any(android.view.View.class); }
                                    @Override public String getDescription() { return "check pin icon"; }
                                    @Override public void perform(androidx.test.espresso.UiController uc, android.view.View v) {
                                        android.view.View icon = v.findViewById(R.id.pinIcon);
                                        if (icon != null && icon.getVisibility() == android.view.View.VISIBLE) {
                                            pinVisible[0] = true;
                                        }
                                    }
                                }));
                if (pinVisible[0]) break;
            } catch (Exception | AssertionError e) { /* retry */ }
            Thread.sleep(250);
        }
        Log.d(TAG, "Phase 5 result: " + pinVisible[0]);
        assertTrue("Pin icon should appear via live WebSocket event", pinVisible[0]);

        // --- Phase 6: Live lock ---
        Log.d(TAG, "=== Phase 6: Live lock ===");
        String lockCommentId = seedComment(urlId, "Lock target from A", ssoTokenA);
        if (lockCommentId == null) {
            lockCommentId = fetchLatestCommentId(urlId);
        }
        Log.d(TAG, "Phase 6: lock target ID=" + lockCommentId);
        assertNotNull("Should have lock target comment ID", lockCommentId);

        launchActivity(urlId, ssoTokenA);
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Lock target from A")))));
                return true;
            } catch (Exception | AssertionError e) { return false; }
        });

        JSONObject phase6Setup = new JSONObject();
        phase6Setup.put("commentId", lockCommentId);
        sync.postData("phase6_setup", phase6Setup);
        sync.signalReady("phase6");
        sync.waitFor("userB", "phase6");

        // Check lock icon within the "Lock target" item specifically
        final boolean[] lockVisible = {false};
        deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .perform(androidx.test.espresso.contrib.RecyclerViewActions.actionOnItem(
                                hasDescendant(withText(containsString("Lock target from A"))),
                                new androidx.test.espresso.ViewAction() {
                                    @Override public org.hamcrest.Matcher<android.view.View> getConstraints() { return org.hamcrest.Matchers.any(android.view.View.class); }
                                    @Override public String getDescription() { return "check lock icon"; }
                                    @Override public void perform(androidx.test.espresso.UiController uc, android.view.View v) {
                                        android.view.View icon = v.findViewById(R.id.lockIcon);
                                        if (icon != null && icon.getVisibility() == android.view.View.VISIBLE) {
                                            lockVisible[0] = true;
                                        }
                                    }
                                }));
                if (lockVisible[0]) break;
            } catch (Exception | AssertionError e) { /* retry */ }
            Thread.sleep(250);
        }
        Log.d(TAG, "Phase 6 result: " + lockVisible[0]);
        assertTrue("Lock icon should appear via live WebSocket event", lockVisible[0]);

        sync.signalReady("done");
    }
}
