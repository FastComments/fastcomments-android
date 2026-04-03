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

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.fastcomments.sdk.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Single-emulator tests for voting operations.
 */
@RunWith(AndroidJUnit4.class)
public class VoteUITests extends UITestBase {

    private String urlId;
    private String ssoToken;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        createTestTenant("android-vote@fctest.com");
        ssoToken = makeSecureSSOToken("vote-user");
    }

    private void postCommentAndWait(String text) throws Exception {
        pollUntil(15000, () -> {
            try {
                onView(withId(R.id.commentInput)).check(matches(isDisplayed()));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });

        onView(withId(R.id.commentInput))
                .perform(click(), typeText(text), closeSoftKeyboard());
        onView(withId(R.id.sendButton)).perform(click());

        pollUntil(10000, () -> {
            try {
                onView(withId(R.id.recyclerViewComments))
                        .check(matches(hasDescendant(withText(containsString(text)))));
                return true;
            } catch (Exception | AssertionError e) {
                return false;
            }
        });
    }

    @Test
    public void testTapUpvote() throws Exception {
        urlId = "vote-up-" + System.currentTimeMillis();
        launchActivity(urlId, ssoToken);
        postCommentAndWait("Vote on me");

        onView(withId(R.id.upVoteButton)).perform(click());

        boolean voteChanged = false;
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.upVoteCount)).check(matches(withText("1")));
                voteChanged = true;
                break;
            } catch (Exception | AssertionError e) {
                Thread.sleep(250);
            }
        }
        assertTrue("Upvote count should show 1", voteChanged);
    }

    @Test
    public void testTapDownvote() throws Exception {
        urlId = "vote-down-" + System.currentTimeMillis();
        launchActivity(urlId, ssoToken);
        postCommentAndWait("Downvote me");

        onView(withId(R.id.downVoteButton)).perform(click());

        boolean voteChanged = false;
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.downVoteCount)).check(matches(withText("1")));
                voteChanged = true;
                break;
            } catch (Exception | AssertionError e) {
                Thread.sleep(250);
            }
        }
        assertTrue("Downvote count should show 1", voteChanged);
    }

    @Test
    public void testToggleVote() throws Exception {
        urlId = "vote-toggle-" + System.currentTimeMillis();
        launchActivity(urlId, ssoToken);
        postCommentAndWait("Toggle my vote");

        // Upvote
        onView(withId(R.id.upVoteButton)).perform(click());

        boolean upvoted = false;
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.upVoteCount)).check(matches(withText("1")));
                upvoted = true;
                break;
            } catch (Exception | AssertionError e) {
                Thread.sleep(250);
            }
        }
        assertTrue("Upvote count should show 1", upvoted);

        // Switch to downvote
        onView(withId(R.id.downVoteButton)).perform(click());

        boolean downvoted = false;
        deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.downVoteCount)).check(matches(withText("1")));
                downvoted = true;
                break;
            } catch (Exception | AssertionError e) {
                Thread.sleep(250);
            }
        }
        assertTrue("Downvote count should show 1 after toggling", downvoted);

        // Verify upvote reset to 0
        onView(withId(R.id.upVoteCount)).check(matches(withText("0")));
    }
}
