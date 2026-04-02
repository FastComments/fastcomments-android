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

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.fastcomments.sdk.R;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Single-emulator tests for reply threading.
 */
@RunWith(AndroidJUnit4.class)
public class ThreadingUITests extends UITestBase {

    private static final String TAG = "ThreadingUITests";
    private String urlId;
    private String ssoToken;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        createTestTenant();
        ssoToken = makeSecureSSOToken("threading-user");
    }

    @Test
    public void testReplyToComment() throws Exception {
        urlId = "threading-reply-" + System.currentTimeMillis();
        launchActivity(urlId, ssoToken);

        // Post parent comment
        pollUntil(15000, () -> {
            try {
                onView(withId(R.id.commentInput)).check(matches(isDisplayed()));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        onView(withId(R.id.commentInput))
                .perform(click(), typeText("Parent comment"), closeSoftKeyboard());
        onView(withId(R.id.sendButton)).perform(click());

        // Wait for parent to appear
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Parent comment")))));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        // Tap reply button and wait for reply mode to activate
        onView(withId(R.id.replyButton)).perform(click());

        // Wait for the reply indicator to appear (confirms input is in reply mode)
        pollUntil(5000, () -> {
            try {
                onView(withId(R.id.replyIndicator)).check(matches(isDisplayed()));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        // Type and submit reply
        Log.d(TAG, "Typing reply...");
        onView(withId(R.id.commentInput))
                .perform(click(), typeText("This is a reply"), closeSoftKeyboard());
        Thread.sleep(500); // Let keyboard dismiss
        Log.d(TAG, "Clicking send for reply...");
        onView(withId(R.id.sendButton)).perform(click());
        Log.d(TAG, "Send clicked, waiting for reply to appear...");

        // Verify reply appears — may need to scroll since it's a child comment
        boolean replyFound = false;
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("This is a reply")))));
                replyFound = true;
                break;
            } catch (Exception | AssertionError e) {
                Thread.sleep(250);
            }
        }
        assertTrue("Reply should appear in comment list", replyFound);

        // Verify parent still exists
        onView(withId(R.id.recyclerViewComments))
                .check(matches(hasDescendant(withText(containsString("Parent comment")))));
    }

    @Ignore("Known issue — show/hide replies toggle may not appear with asTree + maxTreeDepth:1")
    @Test
    public void testShowHideReplies() {
        // Skipped — same known issue as iOS
    }
}
