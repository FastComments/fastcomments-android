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
 * Actor role for live chat — runs on Emulator B.
 * Phase 1: UserB sends a message, UserA verifies receipt.
 * Phase 2: UserA sends a message, UserB verifies it appears live.
 */
@RunWith(AndroidJUnit4.class)
public class LiveChatUserB_UITests extends UITestBase {

    private static final String TAG = "LiveChatUserB";

    private String urlId;
    private String ssoTokenB;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

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
        testTenantEmail = null;
        super.tearDown();
    }

    @Test
    public void testLiveChat_UserB() throws Exception {
        // --- Phase 1: UserB sends a message ---
        Log.d(TAG, "=== Phase 1: Send message ===");
        sync.waitFor("userA", "phase1");

        // Seed a comment so the chat has content when it loads
        seedComment(urlId, "seed", ssoTokenB);

        launchLiveChatActivity(urlId, ssoTokenB);
        Thread.sleep(5000);

        String messageText = "Chat from B " + System.currentTimeMillis();
        onView(withId(R.id.commentInput))
                .perform(click(), typeText(messageText), closeSoftKeyboard());
        onView(withId(R.id.sendButton)).perform(click());

        // Verify own message appears
        boolean ownAppeared = false;
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString(messageText)))));
                ownAppeared = true;
                break;
            } catch (Exception | AssertionError e) { Thread.sleep(250); }
        }
        assertTrue("Own message should appear after sending", ownAppeared);

        JSONObject phase1Data = new JSONObject();
        phase1Data.put("text", messageText);
        sync.postData("phase1", phase1Data);
        sync.signalReady("phase1");
        Log.d(TAG, "Phase 1 complete");

        // --- Phase 2: UserB receives UserA's message ---
        Log.d(TAG, "=== Phase 2: Receive message from UserA ===");
        sync.waitFor("userA", "phase2");

        JSONObject phase2Data = sync.getData("phase2");
        String userAMessage = phase2Data.getString("text");
        Log.d(TAG, "Looking for message: " + userAMessage);

        boolean found = false;
        deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString(userAMessage)))));
                found = true;
                break;
            } catch (Exception | AssertionError e) { Thread.sleep(250); }
        }
        Log.d(TAG, "Phase 2 result: " + found);
        assertTrue("UserA's message should appear live in UserB's chat", found);

        sync.signalReady("phase2_confirmed");
    }
}
