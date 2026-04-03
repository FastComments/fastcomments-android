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
 * Observer role for feed — runs on Emulator A.
 * Phase 1: UserB creates a post via SDK, UserA sees the live banner via WebSocket and taps it.
 * Phase 2: UserA creates a post via SDK, UserB confirms receipt.
 */
@RunWith(AndroidJUnit4.class)
public class FeedUserA_UITests extends UITestBase {

    private static final String TAG = "FeedUserA";

    private String urlId;
    private String ssoTokenA;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        createTestTenant("android-feed@fctest.com");

        urlId = "feed-" + System.currentTimeMillis();
        ssoTokenA = makeSecureSSOToken("userA-feed");
        String ssoTokenB = makeSecureSSOToken("userB-feed");

        JSONObject setupData = new JSONObject();
        setupData.put("tenantId", testTenantId);
        setupData.put("apiKey", testTenantApiKey);
        setupData.put("urlId", urlId);
        setupData.put("ssoTokenB", ssoTokenB);
        sync.postData("setup", setupData);
        sync.signalReady("setup");
    }

    @Test
    public void testFeed_UserA() throws Exception {
        // Launch feed and wait for it to load
        launchFeedActivity(urlId, ssoTokenA);
        Log.d(TAG, "Activity launched, waiting for feed...");

        pollUntil(15000, () -> {
            try {
                // Feed may be empty initially — accept either the recycler or empty state
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

        // --- Phase 1: UserB posts, UserA sees it ---
        Log.d(TAG, "=== Phase 1: See UserB's post ===");
        delay();
        sync.signalReady("phase1");
        sync.waitFor("userB", "phase1");

        JSONObject phase1Data = sync.getData("phase1");
        String postText = phase1Data.getString("text");
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
        assertTrue("New posts banner should appear when UserB posts", bannerVisible);

        // Tap the banner to load the new post
        delay();
        onView(withId(R.id.newPostsBanner)).perform(click());
        Log.d(TAG, "Tapped new posts banner");

        // Verify UserB's post appears in the feed
        boolean found = false;
        deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewFeed))
                        .check(matches(hasDescendant(withText(containsString(postText)))));
                found = true;
                break;
            } catch (Exception | AssertionError e) { Thread.sleep(250); }
        }
        Log.d(TAG, "Phase 1 result: " + found);
        assertTrue("UserB's post should appear after tapping new posts banner", found);
        delay();

        // --- Phase 2: UserA posts via UI, UserB sees it ---
        Log.d(TAG, "=== Phase 2: Create post for UserB ===");
        String myPostText = "Feed post from A " + System.currentTimeMillis();
        onView(withId(R.id.postContentEditText))
                .perform(click(), typeText(myPostText), closeSoftKeyboard());
        onView(withId(R.id.submitPostButton)).perform(click());
        Log.d(TAG, "Submitted post via UI");

        JSONObject phase2Data = new JSONObject();
        phase2Data.put("text", myPostText);
        sync.postData("phase2", phase2Data);
        sync.signalReady("phase2");
        sync.waitFor("userB", "phase2_confirmed");
        Log.d(TAG, "Phase 2 confirmed by UserB");
    }
}
