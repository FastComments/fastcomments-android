package com.fastcomments;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withTagValue;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.fastcomments.sdk.BottomCommentInputView;
import com.fastcomments.sdk.R;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Single-emulator tests for polls: building one in the composer, posting it, voting and changing the vote.
 */
@RunWith(AndroidJUnit4.class)
public class PollUITests extends UITestBase {

    private String ssoToken;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        createTestTenant("android-polls@fctest.com");
        createCustomConfig(new JSONObject().put("pollCreation", 2));
        ssoToken = makeSecureSSOToken("poll-user");
    }

    private boolean waitForView(long timeoutMs, Runnable check) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                check.run();
                return true;
            } catch (Exception | AssertionError e) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ignored) {
                }
            }
        }
        return false;
    }

    private void postPoll(String text, String question, String first, String second) {
        assertTrue("Poll button should appear once polls are enabled", waitForView(20000, () -> onView(
                        withTagValue(is(BottomCommentInputView.POLL_BUTTON_TAG)))
                .check(matches(isDisplayed()))));
        onView(withTagValue(is(BottomCommentInputView.POLL_BUTTON_TAG))).perform(click());

        onView(withId(R.id.pollBuilderQuestion)).perform(typeText(question), closeSoftKeyboard());
        // The builder scrolls inside the composer, so lower options can start out of view.
        onView(allOf(withId(R.id.pollBuilderOptionInput), withHint("Option 1")))
                .perform(scrollTo(), typeText(first), closeSoftKeyboard());
        onView(allOf(withId(R.id.pollBuilderOptionInput), withHint("Option 2")))
                .perform(scrollTo(), typeText(second), closeSoftKeyboard());
        onView(withId(R.id.commentInput)).perform(click(), typeText(text), closeSoftKeyboard());
        onView(withId(R.id.sendButton)).perform(click());

        assertTrue("Posted poll should render", waitForView(15000, () -> onView(withId(R.id.recyclerViewComments))
                .check(matches(hasDescendant(withText(question))))));
    }

    private void tapOption(String label) {
        onView(allOf(withId(R.id.pollOptionLabel), withText(label))).perform(click());
    }

    private boolean waitForPercent(String label, String pct) {
        return waitForView(
                15000, () -> onView(allOf(withId(R.id.pollOptionPct), withText(pct), hasSibling(withText(label))))
                        .check(matches(isDisplayed())));
    }

    @Test
    public void testBuildPostAndVote() {
        launchActivity("poll-vote-" + System.currentTimeMillis(), ssoToken);
        postPoll("Lunch vote", "Lunch plan?", "Tacos", "Ramen");

        tapOption("Tacos");

        assertTrue("Vote should show Tacos at 100%", waitForPercent("Tacos", "100%"));
        onView(withId(R.id.pollSaved)).check(matches(isDisplayed()));
        onView(withId(R.id.pollTotal)).check(matches(withText("1 response")));
    }

    @Test
    public void testChangeVote() {
        launchActivity("poll-change-" + System.currentTimeMillis(), ssoToken);
        postPoll("Lunch vote", "Lunch plan?", "Tacos", "Ramen");
        tapOption("Tacos");
        assertTrue(waitForPercent("Tacos", "100%"));

        onView(withId(R.id.pollChangeVote)).perform(click());
        tapOption("Ramen");

        assertTrue("Changed vote should move to Ramen", waitForPercent("Ramen", "100%"));
        assertTrue(waitForPercent("Tacos", "0%"));
        onView(withId(R.id.pollTotal)).check(matches(withText("1 response")));
    }

    @Test
    public void testHalfWrittenPollBlocksPost() {
        launchActivity("poll-invalid-" + System.currentTimeMillis(), ssoToken);
        assertTrue(waitForView(20000, () -> onView(withTagValue(is(BottomCommentInputView.POLL_BUTTON_TAG)))
                .check(matches(isDisplayed()))));
        onView(withTagValue(is(BottomCommentInputView.POLL_BUTTON_TAG))).perform(click());
        onView(withId(R.id.commentInput)).perform(click(), typeText("No question yet"), closeSoftKeyboard());
        onView(withId(R.id.sendButton)).perform(click());

        onView(withId(R.id.pollBuilderError))
                .check(matches(allOf(isDisplayed(), withText("Enter a question for your poll."))));
    }
}
