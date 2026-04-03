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
import androidx.test.platform.app.InstrumentationRegistry;

import com.fastcomments.sdk.R;

import org.json.JSONObject;
import org.junit.Before;
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
        createTestTenant("android-moderation@fctest.com");
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

    @Test
    public void testBlockShowsBlockedText() throws Exception {
        urlId = "mod-block-" + System.currentTimeMillis();

        // Seed comment as userA
        String ssoTokenA = makeSecureSSOToken("block-userA");
        seedComment(urlId, "Block this comment", ssoTokenA);

        // Launch as userB (different user can block)
        String ssoTokenB = makeSecureSSOToken("block-userB");
        launchActivity(urlId, ssoTokenB);

        // Wait for comment to appear
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Block this comment")))));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        // Tap menu and block
        onView(withId(R.id.commentMenuButton)).perform(click());
        onView(withText(R.string.block_user)).perform(click());

        // Confirm the block dialog
        onView(withText(R.string.block)).perform(click());

        // Verify comment text is replaced with blocked placeholder
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString(
                                InstrumentationRegistry.getInstrumentation().getTargetContext()
                                        .getString(R.string.you_blocked_this_user))))));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        // Verify the blocked user placeholder name is shown
        onView(withId(R.id.recyclerViewComments))
                .check(matches(hasDescendant(withText(containsString(
                        InstrumentationRegistry.getInstrumentation().getTargetContext()
                                .getString(R.string.blocked_user_placeholder))))));

        // Verify the original comment text is no longer visible
        onView(withId(R.id.recyclerViewComments))
                .check(matches(org.hamcrest.Matchers.not(
                        hasDescendant(withText(containsString("Block this comment"))))));
    }

    @Test
    public void testUnblockRestoresComment() throws Exception {
        urlId = "mod-unblock-" + System.currentTimeMillis();

        // Seed comment as userA
        String ssoTokenA = makeSecureSSOToken("unblock-userA");
        seedComment(urlId, "Unblock this comment", ssoTokenA);

        // Launch as userB
        String ssoTokenB = makeSecureSSOToken("unblock-userB");
        launchActivity(urlId, ssoTokenB);

        // Wait for comment to appear
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Unblock this comment")))));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        // Block the user first
        onView(withId(R.id.commentMenuButton)).perform(click());
        onView(withText(R.string.block_user)).perform(click());
        onView(withText(R.string.block)).perform(click());

        // Wait for blocked state
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString(
                                InstrumentationRegistry.getInstrumentation().getTargetContext()
                                        .getString(R.string.you_blocked_this_user))))));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        // Now unblock: tap menu and select unblock
        onView(withId(R.id.commentMenuButton)).perform(click());
        onView(withText(R.string.unblock_user)).perform(click());

        // Confirm the unblock dialog
        onView(withText(R.string.unblock)).perform(click());

        // Verify the original comment text is restored
        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString("Unblock this comment")))));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        // Verify blocked placeholder text is gone
        onView(withId(R.id.recyclerViewComments))
                .check(matches(org.hamcrest.Matchers.not(
                        hasDescendant(withText(containsString(
                                InstrumentationRegistry.getInstrumentation().getTargetContext()
                                        .getString(R.string.you_blocked_this_user)))))));
    }
}
