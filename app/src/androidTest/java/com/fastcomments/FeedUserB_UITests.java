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
 * Actor role for feed — runs on Emulator B.
 * Phase 1: UserB creates a post via SDK, UserA verifies receipt.
 * Phase 2: UserA creates a post, UserB relaunches and verifies it appears.
 */
@RunWith(AndroidJUnit4.class)
public class FeedUserB_UITests extends UITestBase {

    private static final String TAG = "FeedUserB";

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
    public void testFeed_UserB() throws Exception {
        // --- Phase 1: UserB creates a post ---
        Log.d(TAG, "=== Phase 1: Create post ===");
        sync.waitFor("userA", "phase1");

        launchFeedActivity(urlId, ssoTokenB);
        pollUntil(15000, () -> {
            try {
                try {
                    onView(withId(R.id.recyclerViewFeed)).check(matches(isDisplayed()));
                    return true;
                } catch (Exception | AssertionError e) {
                    onView(withId(R.id.emptyStateView)).check(matches(isDisplayed()));
                    return true;
                }
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        String postText = "Feed post from B " + System.currentTimeMillis();
        onView(withId(R.id.postContentEditText))
                .perform(click(), typeText(postText), closeSoftKeyboard());
        onView(withId(R.id.submitPostButton)).perform(click());
        Log.d(TAG, "Submitted post via UI");

        JSONObject phase1Data = new JSONObject();
        phase1Data.put("text", postText);
        sync.postData("phase1", phase1Data);
        sync.signalReady("phase1");
        Log.d(TAG, "Phase 1 complete");
        delay();

        // --- Phase 2: UserB sees UserA's post ---
        Log.d(TAG, "=== Phase 2: See UserA's post ===");
        sync.waitFor("userA", "phase2");

        JSONObject phase2Data = sync.getData("phase2");
        String userAPostText = phase2Data.getString("text");
        Log.d(TAG, "Looking for new posts banner...");

        // Wait for the "Show X New Posts" banner to appear via live WebSocket event
        boolean bannerVisible = false;
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.newPostsBanner)).check(matches(isDisplayed()));
                bannerVisible = true;
                break;
            } catch (Exception | AssertionError e) { Thread.sleep(250); }
        }
        Log.d(TAG, "Banner visible: " + bannerVisible);
        assertTrue("New posts banner should appear when UserA posts", bannerVisible);

        // Tap the banner to load the new post
        delay();
        onView(withId(R.id.newPostsBanner)).perform(click());
        Log.d(TAG, "Tapped new posts banner");

        // Verify UserA's post appears in the feed
        boolean found = false;
        deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewFeed))
                        .check(matches(hasDescendant(withText(containsString(userAPostText)))));
                found = true;
                break;
            } catch (Exception | AssertionError e) { Thread.sleep(250); }
        }
        Log.d(TAG, "Phase 2 result: " + found);
        assertTrue("UserA's post should appear in UserB's feed", found);
        delay();

        sync.signalReady("phase2_confirmed");
    }
}
