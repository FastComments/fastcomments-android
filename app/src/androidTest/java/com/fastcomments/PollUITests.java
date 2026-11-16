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
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;

import android.content.Context;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.fastcomments.sdk.R;
import java.text.NumberFormat;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Single-emulator tests for polls: building one in the composer, posting it, voting and changing the vote. Espresso
 * waits on the SDK's API calls through an idling resource, and expected text comes from the SDK's resources so the
 * tests pass in any device language.
 */
@RunWith(AndroidJUnit4.class)
public class PollUITests extends UITestBase {

    private String ssoToken;
    private SdkIdlingResource apiIdling;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        createTestTenant("android-polls@fctest.com");
        createCustomConfig(new JSONObject().put("pollCreation", 2));
        ssoToken = makeSecureSSOToken("poll-user");
    }

    @After
    public void unregisterIdling() {
        if (apiIdling != null) {
            IdlingRegistry.getInstance().unregister(apiIdling);
        }
    }

    private void launchPolls(String urlId) {
        launchActivity(urlId, ssoToken);
        scenario.onActivity(activity -> apiIdling = new SdkIdlingResource(
                "fastcomments-api", activity.getCommentsView().getSDK()));
        IdlingRegistry.getInstance().register(apiIdling);
    }

    private static Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private static String percent(int pct) {
        return context().getString(R.string.fastcomments_poll_percent, pct);
    }

    private static String responses(int count) {
        return context()
                .getResources()
                .getQuantityString(
                        R.plurals.fastcomments_poll_responses,
                        count,
                        NumberFormat.getInstance().format(count));
    }

    private void postPoll(String text, String question, String first, String second) {
        onView(withId(R.id.fastcomments_poll_button)).perform(click());

        onView(withId(R.id.pollBuilderQuestion)).perform(typeText(question), closeSoftKeyboard());
        // The builder scrolls inside the composer, so lower options can start out of view.
        onView(allOf(
                        withId(R.id.pollBuilderOptionInput),
                        withHint(context().getString(R.string.fastcomments_poll_option_placeholder, 1))))
                .perform(scrollTo(), typeText(first), closeSoftKeyboard());
        onView(allOf(
                        withId(R.id.pollBuilderOptionInput),
                        withHint(context().getString(R.string.fastcomments_poll_option_placeholder, 2))))
                .perform(scrollTo(), typeText(second), closeSoftKeyboard());
        onView(withId(R.id.commentInput)).perform(click(), typeText(text), closeSoftKeyboard());
        onView(withId(R.id.sendButton)).perform(click());

        onView(withId(R.id.recyclerViewComments)).check(matches(hasDescendant(withText(question))));
    }

    private void tapOption(String label) {
        onView(allOf(withId(R.id.pollOptionLabel), withText(label))).perform(click());
    }

    private void checkPercent(String label, int pct) {
        onView(allOf(withId(R.id.pollOptionPct), withText(percent(pct)), hasSibling(withText(label))))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testBuildPostAndVote() {
        launchPolls("poll-vote-" + System.currentTimeMillis());
        postPoll("Lunch vote", "Lunch plan?", "Tacos", "Ramen");

        tapOption("Tacos");

        checkPercent("Tacos", 100);
        onView(withId(R.id.pollSaved)).check(matches(isDisplayed()));
        onView(withId(R.id.pollTotal)).check(matches(withText(responses(1))));
    }

    @Test
    public void testChangeVote() {
        launchPolls("poll-change-" + System.currentTimeMillis());
        postPoll("Lunch vote", "Lunch plan?", "Tacos", "Ramen");
        tapOption("Tacos");
        checkPercent("Tacos", 100);

        onView(withId(R.id.pollChangeVote)).perform(click());
        tapOption("Ramen");

        checkPercent("Ramen", 100);
        checkPercent("Tacos", 0);
        onView(withId(R.id.pollTotal)).check(matches(withText(responses(1))));
    }

    @Test
    public void testHalfWrittenPollBlocksPost() {
        launchPolls("poll-invalid-" + System.currentTimeMillis());
        onView(withId(R.id.fastcomments_poll_button)).perform(click());
        onView(withId(R.id.commentInput)).perform(click(), typeText("No question yet"), closeSoftKeyboard());
        onView(withId(R.id.sendButton)).perform(click());

        onView(withId(R.id.pollBuilderError))
                .check(matches(allOf(
                        isDisplayed(),
                        withText(context().getString(R.string.fastcomments_poll_error_question_required)))));
    }
}
