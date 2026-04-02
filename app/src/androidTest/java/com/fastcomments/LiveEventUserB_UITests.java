package com.fastcomments;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
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
 * Phase 1: Types and submits a comment, then signals UserA to verify it appeared via live.
 */
@RunWith(AndroidJUnit4.class)
public class LiveEventUserB_UITests extends UITestBase {

    private static final String TAG = "LiveEventUserB";

    private String urlId;
    private String ssoTokenB;

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
    }

    @After
    @Override
    public void tearDown() throws Exception {
        // UserB does not own the tenant — skip tenant deletion
        testTenantEmail = null;
        super.tearDown();
    }

    @Test
    public void testLiveComment_UserB() throws Exception {
        // Wait for UserA to be ready for phase 1
        sync.waitFor("userA", "phase1");

        // Launch the app and wait for widget to load
        launchActivity(urlId, ssoTokenB);
        Log.d(TAG, "Activity launched, waiting for comment input...");

        // Wait for comment input to appear (widget loaded)
        pollUntil(15000, () -> {
            try {
                onView(withId(R.id.commentInput)).check(matches(isDisplayed()));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });
        Log.d(TAG, "Comment input visible, typing comment...");

        // Type and submit a comment
        String commentText = "Live from B " + System.currentTimeMillis();
        onView(withId(R.id.commentInput))
                .perform(click(), typeText(commentText), closeSoftKeyboard());
        onView(withId(R.id.sendButton))
                .perform(click());
        Log.d(TAG, "Comment submitted: " + commentText);

        // Wait for own comment to appear in the list (confirms it was posted)
        boolean ownCommentAppeared = false;
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString(commentText)))));
                ownCommentAppeared = true;
                break;
            } catch (Exception | AssertionError e) {
                // not yet
            }
            Thread.sleep(250);
        }
        Log.d(TAG, "Own comment appeared: " + ownCommentAppeared);
        assertTrue("Own comment should appear in list after posting", ownCommentAppeared);

        // Post the comment text so UserA knows what to look for, then signal done
        JSONObject phase1Data = new JSONObject();
        phase1Data.put("text", commentText);
        sync.postData("phase1", phase1Data);
        sync.signalReady("phase1");
        Log.d(TAG, "Signaled phase1 ready, waiting for UserA done...");

        // Wait for UserA to finish verifying
        sync.waitFor("userA", "done");
    }
}
