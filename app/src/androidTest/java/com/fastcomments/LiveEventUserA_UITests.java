package com.fastcomments;

import static androidx.test.espresso.Espresso.onView;
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
 * Observer role — runs on Emulator A.
 * Phase 1: Waits for UserB to post a comment and verifies it appears via live WebSocket.
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
        createTestTenant();

        urlId = "live-" + System.currentTimeMillis();
        ssoTokenA = makeSecureSSOToken("userA-live");
        String ssoTokenB = makeSecureSSOToken("userB-live");

        // Post setup data for UserB
        JSONObject setupData = new JSONObject();
        setupData.put("tenantId", testTenantId);
        setupData.put("apiKey", testTenantApiKey);
        setupData.put("urlId", urlId);
        setupData.put("ssoTokenB", ssoTokenB);
        sync.postData("setup", setupData);
        sync.signalReady("setup");
    }

    @Test
    public void testLiveComment_UserA() throws Exception {
        // Launch and wait for the widget to load
        launchActivity(urlId, ssoTokenA);
        Log.d(TAG, "Activity launched, waiting for RecyclerView...");

        // Wait for RecyclerView to appear
        pollUntil(15000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments)).check(matches(isDisplayed()));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });
        Log.d(TAG, "RecyclerView visible, signaling ready for phase1");

        // Signal ready for phase 1 and wait for UserB to post the comment
        sync.signalReady("phase1");
        sync.waitFor("userB", "phase1");

        // Get the comment text UserB posted
        JSONObject phase1Data = sync.getData("phase1");
        String commentText = phase1Data.getString("text");
        Log.d(TAG, "Looking for live comment: " + commentText);

        // Wait for the comment to appear via live WebSocket — no relaunch fallback
        boolean found = false;
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString(commentText)))));
                found = true;
                break;
            } catch (Exception | AssertionError e) {
                // not yet
            }
            Thread.sleep(250);
        }

        Log.d(TAG, "Comment found via live: " + found);
        assertTrue("Live comment should appear via WebSocket without reload", found);

        sync.signalReady("done");
    }
}
