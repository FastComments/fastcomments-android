package com.fastcomments;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
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
 * Single-emulator tests for @mention functionality.
 * Verifies mention search, selection, submission, and dismissal.
 */
@RunWith(AndroidJUnit4.class)
public class MentionUITests extends UITestBase {

    private static final String TAG = "MentionUITests";
    private String urlId;
    private String ssoToken;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        createTestTenant("android-mention@fctest.com");
        ssoToken = makeSecureSSOToken("mentioner-user");
    }

    @Test
    public void testMentionSearchAndSelect() throws Exception {
        urlId = "mention-search-" + System.currentTimeMillis();

        // Seed comments with different SSO users to register them for mention search.
        // makeSecureSSOToken("alice1-mention") => username "Tester alice1"
        String aliceToken = makeSecureSSOToken("alice1-mention");
        String bobToken = makeSecureSSOToken("bob234-mention");
        String carolToken = makeSecureSSOToken("carol5-mention");

        seedComment(urlId, "Hello from Alice", aliceToken);
        seedComment(urlId, "Hello from Bob", bobToken);
        seedComment(urlId, "Hello from Carol", carolToken);
        Log.d(TAG, "Seeded 3 comments from different SSO users");

        // Launch as the mentioner user
        launchActivity(urlId, ssoToken);

        // Wait for comment input to appear
        pollUntil(15000, () -> {
            try {
                onView(withId(R.id.commentInput)).check(matches(isDisplayed()));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        // Type @Tester a to trigger mention search (should match "Tester alice1")
        onView(withId(R.id.commentInput))
                .perform(click(), typeText("@Tester a"), closeSoftKeyboard());

        // Poll until the mention popup appears with "Tester alice1"
        boolean popupFound = false;
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withText(containsString("Tester alice1")))
                        .inRoot(isPlatformPopup())
                        .check(matches(isDisplayed()));
                popupFound = true;
                break;
            } catch (Exception | AssertionError e) {
                Thread.sleep(250);
            }
        }
        assertTrue("Mention popup should show 'Tester alice1'", popupFound);

        // Click on the suggestion to select it
        onView(withText(containsString("Tester alice1")))
                .inRoot(isPlatformPopup())
                .perform(click());

        // Type additional text and submit
        onView(withId(R.id.commentInput))
                .perform(typeText("hello!"), closeSoftKeyboard());
        onView(withId(R.id.sendButton))
                .perform(click());

        // Verify the posted comment appears with the mentioned username
        boolean commentFound = false;
        deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Tester alice1")))));
                commentFound = true;
                break;
            } catch (Exception | AssertionError e) {
                Thread.sleep(250);
            }
        }
        assertTrue("Posted comment should contain the mentioned username", commentFound);
    }

    @Test
    public void testMentionPopupDismissesOnSpace() throws Exception {
        urlId = "mention-dismiss-" + System.currentTimeMillis();

        // Seed a user so mention search has results
        String aliceToken = makeSecureSSOToken("alice1-mention");
        seedComment(urlId, "Hello from Alice", aliceToken);

        launchActivity(urlId, ssoToken);

        // Wait for comment input
        pollUntil(15000, () -> {
            try {
                onView(withId(R.id.commentInput)).check(matches(isDisplayed()));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        // Type @Tester a to trigger mention search
        onView(withId(R.id.commentInput))
                .perform(click(), typeText("@Tester a"), closeSoftKeyboard());

        // Wait for popup to appear
        boolean popupFound = false;
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withText(containsString("Tester alice1")))
                        .inRoot(isPlatformPopup())
                        .check(matches(isDisplayed()));
                popupFound = true;
                break;
            } catch (Exception | AssertionError e) {
                Thread.sleep(250);
            }
        }
        assertTrue("Mention popup should appear before dismissal test", popupFound);

        // Type a space to dismiss the mention (triggers cancelMention)
        onView(withId(R.id.commentInput))
                .perform(typeText(" "));

        // Verify popup is gone
        boolean popupDismissed = false;
        long dismissDeadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < dismissDeadline) {
            try {
                onView(withText(containsString("Tester alice1")))
                        .inRoot(isPlatformPopup())
                        .check(matches(isDisplayed()));
                // Still showing, wait and retry
                Thread.sleep(250);
            } catch (Exception | AssertionError e) {
                // Not found or root gone — popup is dismissed
                popupDismissed = true;
                break;
            }
        }
        assertTrue("Mention popup should be dismissed after typing a space", popupDismissed);
    }
}
