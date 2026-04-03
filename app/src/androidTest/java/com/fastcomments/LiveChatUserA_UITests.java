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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.test.ext.junit.runners.AndroidJUnit4;

/**
 * Observer role for live chat — runs on Emulator A.
 * Phase 1: UserB sends a message, UserA verifies it appears live.
 * Phase 2: UserA sends a message, UserB confirms receipt.
 */
@RunWith(AndroidJUnit4.class)
public class LiveChatUserA_UITests extends UITestBase {

    private static final String TAG = "LiveChatUserA";

    private String urlId;
    private String ssoTokenA;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        createTestTenant("android-live-chat@fctest.com");

        urlId = "chat-" + System.currentTimeMillis();
        ssoTokenA = makeSecureSSOToken("userA-chat");
        String ssoTokenB = makeSecureSSOToken("userB-chat");

        JSONObject setupData = new JSONObject();
        setupData.put("tenantId", testTenantId);
        setupData.put("apiKey", testTenantApiKey);
        setupData.put("urlId", urlId);
        setupData.put("ssoTokenB", ssoTokenB);
        sync.postData("setup", setupData);
        sync.signalReady("setup");
    }

    @Test
    public void testLiveChat_UserA() throws Exception {
        launchLiveChatActivity(urlId, ssoTokenA);
        Log.d(TAG, "Activity launched, waiting for connection...");

        // Wait for the header to show "Live" (means WS connected and SDK loaded)
        pollUntil(15000, () -> {
            try {
                onView(withId(R.id.connectionStatusText)).check(matches(withText(R.string.live_chat_live)));
                return true;
            } catch (Exception | AssertionError e) { return false; }
        });
        Log.d(TAG, "Connected");

        // --- Phase 1: UserB sends, UserA receives ---
        Log.d(TAG, "=== Phase 1: Receive message from UserB ===");
        sync.signalReady("phase1");
        sync.waitFor("userB", "phase1");

        JSONObject phase1Data = sync.getData("phase1");
        String messageText = phase1Data.getString("text");
        Log.d(TAG, "Looking for message: " + messageText);

        boolean found = false;
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString(messageText)))));
                found = true;
                break;
            } catch (Exception | AssertionError e) { Thread.sleep(250); }
        }
        Log.d(TAG, "Phase 1 result: " + found);
        assertTrue("UserB's message should appear in UserA's chat", found);

        // Verify user count is displayed now that both users are connected
        boolean userCountVisible = false;
        deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.userCountText)).check(matches(isDisplayed()));
                userCountVisible = true;
                break;
            } catch (Exception | AssertionError e) { Thread.sleep(250); }
        }
        Log.d(TAG, "User count visible: " + userCountVisible);
        assertTrue("User count should be visible in header", userCountVisible);

        // --- Phase 2: UserA sends, UserB receives ---
        Log.d(TAG, "=== Phase 2: Send message to UserB ===");
        String myMessage = "Hello from A " + System.currentTimeMillis();
        onView(withId(R.id.commentInput))
                .perform(click(), typeText(myMessage), closeSoftKeyboard());
        onView(withId(R.id.sendButton)).perform(click());

        // Verify own message appears
        boolean ownAppeared = false;
        deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString(myMessage)))));
                ownAppeared = true;
                break;
            } catch (Exception | AssertionError e) { Thread.sleep(250); }
        }
        assertTrue("Own message should appear after sending", ownAppeared);

        JSONObject phase2Data = new JSONObject();
        phase2Data.put("text", myMessage);
        sync.postData("phase2", phase2Data);
        sync.signalReady("phase2");
        sync.waitFor("userB", "phase2_confirmed");
        Log.d(TAG, "Phase 2 confirmed by UserB");
    }
}
