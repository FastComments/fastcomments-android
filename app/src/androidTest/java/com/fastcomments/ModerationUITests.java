package com.fastcomments;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.fastcomments.sdk.R;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Single-emulator tests for moderation features (pin, lock, flag).
 */
@RunWith(AndroidJUnit4.class)
public class ModerationUITests extends UITestBase {

    private String urlId;
    private String ssoToken;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        createTestTenant();
        ssoToken = makeSecureSSOToken("mod-user");
    }

    @Test
    public void testPinShowsIcon() throws Exception {
        urlId = "mod-pin-" + System.currentTimeMillis();

        String commentId = seedComment(urlId, "Pinned comment", ssoToken);
        if (commentId == null) {
            commentId = fetchLatestCommentId(urlId);
        }

        // Launch with admin SSO first to register the admin user, then pin
        String adminSso = makeSecureSSOToken("mod-admin", true);
        launchActivity(urlId, adminSso);
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Pinned comment")))));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        // Now pin (admin user is registered with the tenant)
        pinComment(commentId, adminSso);

        // Relaunch to see the updated pin state
        launchActivity(urlId, ssoToken);
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Pinned comment")))));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        // Verify pin icon is visible
        boolean pinVisible = false;
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.pinIcon)).check(matches(isDisplayed()));
                pinVisible = true;
                break;
            } catch (Exception | AssertionError e) {
                Thread.sleep(250);
            }
        }
        assertTrue("Pin icon should be visible for pinned comment", pinVisible);
    }

    @Test
    public void testLockShowsIcon() throws Exception {
        urlId = "mod-lock-" + System.currentTimeMillis();

        String commentId = seedComment(urlId, "Locked comment", ssoToken);
        if (commentId == null) {
            commentId = fetchLatestCommentId(urlId);
        }

        // Launch with admin SSO first to register the admin user, then lock
        String adminSso = makeSecureSSOToken("mod-admin", true);
        launchActivity(urlId, adminSso);
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Locked comment")))));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        lockComment(commentId, adminSso);

        // Relaunch to see the updated lock state
        launchActivity(urlId, ssoToken);

        // Wait for comment to appear
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Locked comment")))));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        // Verify lock icon is visible
        boolean lockVisible = false;
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.lockIcon)).check(matches(isDisplayed()));
                lockVisible = true;
                break;
            } catch (Exception | AssertionError e) {
                Thread.sleep(250);
            }
        }
        assertTrue("Lock icon should be visible for locked comment", lockVisible);
    }

    @Test
    public void testFlagViaMenu() throws Exception {
        urlId = "mod-flag-" + System.currentTimeMillis();

        // Seed comment as userA
        String ssoTokenA = makeSecureSSOToken("flag-userA");
        seedComment(urlId, "Flag this comment", ssoTokenA);

        // Launch as userB (different user can flag)
        String ssoTokenB = makeSecureSSOToken("flag-userB");
        launchActivity(urlId, ssoTokenB);

        // Wait for comment to appear
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Flag this comment")))));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        // Tap menu and flag
        onView(withId(R.id.commentMenuButton)).perform(click());
        onView(withText(R.string.flag_comment)).perform(click());

        // Verify the comment is still visible (flag doesn't remove it)
        Thread.sleep(1000);
        onView(withId(R.id.recyclerViewComments))
                .check(matches(hasDescendant(withText(containsString("Flag this comment")))));
    }

    @Ignore("Known issue — block UI state not reliable, same as iOS")
    @Test
    public void testBlockShowsBlockedText() {
    }

    @Ignore("Known issue — unblock UI re-render not reliable, same as iOS")
    @Test
    public void testUnblockRestoresComment() {
    }
}
