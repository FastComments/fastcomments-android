package com.fastcomments.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;
import com.fastcomments.core.CommentWidgetConfig;
import com.fastcomments.model.CommentPoll;
import com.fastcomments.model.CommentPollOption;
import com.fastcomments.model.PollPrivacy;
import com.fastcomments.model.PollVotingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
public class PollCardViewTest {

    private Context context;
    private FastCommentsSDK sdk;
    private PollCardView card;

    @Before
    public void setup() {
        context = RuntimeEnvironment.getApplication();
        CommentWidgetConfig config = new CommentWidgetConfig("tenant", "url");
        config.pollVoting = PollVotingMode.NUMBER_1;
        sdk = new FastCommentsSDK(config, true);
        card = new PollCardView(context);
    }

    private RenderableComment bind(CommentPoll poll) {
        RenderableComment comment = new RenderableComment(PollHelperTest.makePollComment(poll));
        card.bind(sdk, comment);
        return comment;
    }

    private TextView text(int id) {
        return card.findViewById(id);
    }

    private boolean visible(int id) {
        return card.findViewById(id).getVisibility() == View.VISIBLE;
    }

    @Test
    public void unvotedPollHidesResults() {
        bind(PollHelperTest.makePoll(1, 2));

        assertEquals("Best color?", text(R.id.pollQuestion).getText().toString());
        assertEquals(2, card.getOptionCount());
        assertEquals(
                "Option 0",
                ((TextView) card.getOptionView(0).findViewById(R.id.pollOptionLabel))
                        .getText()
                        .toString());
        for (int i = 0; i < 2; i++) {
            assertEquals(0, card.getOptionBarLevel(i));
            assertEquals(
                    0f, card.getOptionView(i).findViewById(R.id.pollOptionPct).getAlpha(), 0f);
            assertFalse(card.getOptionView(i).isSelected());
            assertTrue(card.getOptionView(i).isEnabled());
        }

        assertEquals("3 responses", text(R.id.pollTotal).getText().toString());
        assertEquals("Votes are anonymous", text(R.id.pollPrivacy).getText().toString());
        assertFalse(visible(R.id.pollCloses));
        assertFalse(visible(R.id.pollError));
        assertFalse(visible(R.id.pollSaved));
        assertFalse(visible(R.id.pollChangeVote));
        assertTrue(visible(R.id.pollToggleResults));
        assertEquals("Show results", text(R.id.pollToggleResults).getText().toString());
        assertFalse(visible(R.id.pollVoters));
    }

    @Test
    public void singularResponseCount() {
        bind(PollHelperTest.makePoll(1, 0));
        assertEquals("1 response", text(R.id.pollTotal).getText().toString());
    }

    @Test
    public void votedPollShowsResultsAndSelection() {
        CommentPoll poll = PollHelperTest.makePoll(1, 2).privacy(PollPrivacy.NUMBER_2);
        RenderableComment comment = new RenderableComment(PollHelperTest.makePollComment(poll));
        comment.getComment().setMyPollVotes(Collections.singletonList("o1"));
        card.bind(sdk, comment);

        assertFalse(card.getOptionView(0).isSelected());
        assertTrue(card.getOptionView(1).isSelected());
        assertEquals(3300, card.getOptionBarLevel(0));
        assertEquals(6700, card.getOptionBarLevel(1));
        TextView pct = card.getOptionView(1).findViewById(R.id.pollOptionPct);
        assertEquals("67%", pct.getText().toString());
        assertEquals(1f, pct.getAlpha(), 0f);
        assertEquals(
                View.VISIBLE,
                card.getOptionView(1).findViewById(R.id.pollOptionCheck).getVisibility());
        assertEquals(
                View.GONE,
                card.getOptionView(0).findViewById(R.id.pollOptionCheck).getVisibility());

        assertTrue(visible(R.id.pollSaved));
        assertTrue(visible(R.id.pollChangeVote));
        assertFalse(visible(R.id.pollToggleResults));
        assertTrue(visible(R.id.pollVoters));
        assertEquals(
                "Individual responses are public.",
                text(R.id.pollPrivacy).getText().toString());
    }

    @Test
    public void changeVoteHidesResultsAgain() {
        RenderableComment comment =
                new RenderableComment(PollHelperTest.makePollComment(PollHelperTest.makePoll(1, 2)));
        comment.getComment().setMyPollVotes(Collections.singletonList("o0"));
        card.bind(sdk, comment);

        card.findViewById(R.id.pollChangeVote).performClick();
        assertEquals(Boolean.FALSE, comment.pollShowResults);
        assertEquals(0, card.getOptionBarLevel(0));
        assertFalse(visible(R.id.pollChangeVote));
        assertTrue(visible(R.id.pollSaved));
    }

    @Test
    public void toggleShowsAndHidesResults() {
        RenderableComment comment = bind(PollHelperTest.makePoll(3, 1));
        card.findViewById(R.id.pollToggleResults).performClick();
        assertEquals(Boolean.TRUE, comment.pollShowResults);
        assertEquals(7500, card.getOptionBarLevel(0));
        assertEquals("Hide results", text(R.id.pollToggleResults).getText().toString());

        card.findViewById(R.id.pollToggleResults).performClick();
        assertEquals(0, card.getOptionBarLevel(0));
        assertEquals("Show results", text(R.id.pollToggleResults).getText().toString());
    }

    @Test
    public void lockedPollOffersNoResultsToggle() {
        CommentPoll poll =
                PollHelperTest.makePoll(0, 0).requireVoteToSeeResults(true).totalVotes(4);
        bind(poll);
        assertEquals("4 responses", text(R.id.pollTotal).getText().toString());
        assertFalse(visible(R.id.pollToggleResults));
        assertEquals(0, card.getOptionBarLevel(0));
    }

    @Test
    public void closedPollIsReadOnly() {
        CommentPoll poll =
                PollHelperTest.makePoll(1, 3).closesAt(OffsetDateTime.now().minusDays(1));
        bind(poll);

        assertEquals("Poll closed", text(R.id.pollCloses).getText().toString());
        assertTrue(visible(R.id.pollCloses));
        assertFalse(card.getOptionView(0).isEnabled());
        assertEquals(7500, card.getOptionBarLevel(1));
        assertFalse(visible(R.id.pollToggleResults));
        assertFalse(visible(R.id.pollChangeVote));
    }

    @Test
    public void openPollWithEndDateShowsWhenItCloses() {
        bind(PollHelperTest.makePoll(1, 1).closesAt(OffsetDateTime.now().plusDays(3)));
        assertTrue(visible(R.id.pollCloses));
        assertTrue(text(R.id.pollCloses).getText().toString().startsWith("Poll closes "));
    }

    @Test
    public void loggedOutTapExplainsWhyInsteadOfVoting() {
        sdk.getConfig().pollVoting = PollVotingMode.NUMBER_0;
        RenderableComment comment = bind(PollHelperTest.makePoll(1, 1));

        card.getOptionView(0).performClick();
        assertFalse(comment.pollSaving);
        assertEquals("Log in to vote", comment.pollError);
        assertTrue(visible(R.id.pollError));
        assertEquals("Log in to vote", text(R.id.pollError).getText().toString());
    }

    @Test
    public void tappingYourOwnChoiceOnlyShowsResults() {
        RenderableComment comment =
                new RenderableComment(PollHelperTest.makePollComment(PollHelperTest.makePoll(1, 2)));
        comment.getComment().setMyPollVotes(Collections.singletonList("o0"));
        comment.pollShowResults = false;
        card.bind(sdk, comment);

        card.getOptionView(0).performClick();
        assertFalse(comment.pollSaving);
        assertEquals(Boolean.TRUE, comment.pollShowResults);
        assertEquals(3300, card.getOptionBarLevel(0));
    }

    @Test
    public void liveUpdateWithSameOptionsPatchesInPlace() {
        RenderableComment comment =
                new RenderableComment(PollHelperTest.makePollComment(PollHelperTest.makePoll(1, 1)));
        comment.getComment().setMyPollVotes(Collections.singletonList("o0"));
        card.bind(sdk, comment);
        View firstOption = card.getOptionView(0);

        List<CommentPollOption> options = new ArrayList<>();
        options.add(new CommentPollOption().id("o0").label("Option 0").votes(1));
        options.add(new CommentPollOption().id("o1").label("Option 1").votes(3));
        comment.getComment().getPoll().options(options).totalVotes(4);
        card.bind(sdk, comment);

        assertSame(firstOption, card.getOptionView(0));
        assertEquals(
                "75%",
                ((TextView) card.getOptionView(1).findViewById(R.id.pollOptionPct))
                        .getText()
                        .toString());
        assertEquals("4 responses", text(R.id.pollTotal).getText().toString());
    }

    @Test
    public void patchCountsOnlyMovesTheNumbers() {
        RenderableComment comment =
                new RenderableComment(PollHelperTest.makePollComment(PollHelperTest.makePoll(1, 1)));
        comment.getComment().setMyPollVotes(Collections.singletonList("o0"));
        card.bind(sdk, comment);
        Drawable markBackground =
                card.getOptionView(0).findViewById(R.id.pollOptionMark).getBackground();
        CharSequence question = text(R.id.pollQuestion).getText();

        List<CommentPollOption> options = new ArrayList<>();
        options.add(new CommentPollOption().id("o0").label("Option 0").votes(1));
        options.add(new CommentPollOption().id("o1").label("Option 1").votes(3));
        comment.getComment().getPoll().options(options).totalVotes(4);
        card.patchCounts(sdk, comment);

        assertSame(
                markBackground,
                card.getOptionView(0).findViewById(R.id.pollOptionMark).getBackground());
        assertSame(question, text(R.id.pollQuestion).getText());
        assertEquals(
                "75%",
                ((TextView) card.getOptionView(1).findViewById(R.id.pollOptionPct))
                        .getText()
                        .toString());
        // The bar slides from its old width like the web's CSS transition.
        assertEquals(5000, card.getOptionBarLevel(1));
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
        assertEquals(7500, card.getOptionBarLevel(1));
        assertEquals("Option 1, 75%", card.getOptionView(1).getContentDescription());
        assertEquals("4 responses", text(R.id.pollTotal).getText().toString());
    }

    @Test
    public void patchCountsKeepsHiddenBarsEmpty() {
        RenderableComment comment = bind(PollHelperTest.makePoll(1, 1));

        comment.getComment().getPoll().getOptions().get(1).votes(5);
        comment.getComment().getPoll().totalVotes(6);
        card.patchCounts(sdk, comment);

        assertEquals(0, card.getOptionBarLevel(1));
        assertEquals("Option 1", card.getOptionView(1).getContentDescription());
        assertEquals("6 responses", text(R.id.pollTotal).getText().toString());
    }

    @Test
    public void patchCountsShowsVotersOnceSomeoneVotes() {
        RenderableComment comment = bind(PollHelperTest.makePoll(0, 0).privacy(PollPrivacy.NUMBER_2));
        assertFalse(visible(R.id.pollVoters));

        comment.getComment().getPoll().getOptions().get(0).votes(1);
        comment.getComment().getPoll().totalVotes(1);
        card.patchCounts(sdk, comment);

        assertTrue(visible(R.id.pollVoters));
    }

    @Test
    public void patchCountsRebindsWhenTheCloseDateMoves() {
        RenderableComment comment = bind(PollHelperTest.makePoll(1, 1));

        comment.getComment().getPoll().closesAt(OffsetDateTime.now().minusMinutes(1));
        card.patchCounts(sdk, comment);

        assertTrue(visible(R.id.pollCloses));
        assertEquals("Poll closed", text(R.id.pollCloses).getText().toString());
    }

    @Test
    public void patchCountsRebuildsChangedOptions() {
        RenderableComment comment = bind(PollHelperTest.makePoll(1, 1));

        comment.getComment().setPoll(PollHelperTest.makePoll(1, 1, 1));
        card.patchCounts(sdk, comment);

        assertEquals(3, card.getOptionCount());
    }

    @Test
    public void structuralChangeKeepsExistingOptionViews() {
        RenderableComment comment = bind(PollHelperTest.makePoll(1, 1));
        View firstOption = card.getOptionView(0);

        comment.getComment().setPoll(PollHelperTest.makePoll(1, 1, 1));
        card.bind(sdk, comment);
        assertEquals(3, card.getOptionCount());
        assertSame(firstOption, card.getOptionView(0));
        assertEquals("Option 2", label(2));

        comment.getComment().setPoll(PollHelperTest.makePoll(1));
        card.bind(sdk, comment);
        assertEquals(1, card.getOptionCount());
        assertSame(firstOption, card.getOptionView(0));
    }

    @Test
    public void recycledCardRebindsOptionViewsToTheNewPoll() {
        bind(PollHelperTest.makePoll(1, 1));
        View firstOption = card.getOptionView(0);
        View secondOption = card.getOptionView(1);
        Drawable mark = card.getOptionView(1).findViewById(R.id.pollOptionMark).getBackground();

        List<CommentPollOption> options = new ArrayList<>();
        options.add(new CommentPollOption().id("b0").label("Tacos").votes(2));
        options.add(new CommentPollOption().id("b1").label("Ramen").votes(1));
        RenderableComment other = new RenderableComment(PollHelperTest.makePollComment(
                PollHelperTest.makePoll().options(options).totalVotes(3)));
        other.getComment().setMyPollVotes(Collections.singletonList("b1"));
        card.bind(sdk, other);

        assertSame(firstOption, card.getOptionView(0));
        assertSame(secondOption, card.getOptionView(1));
        assertSame(mark, card.getOptionView(1).findViewById(R.id.pollOptionMark).getBackground());
        assertEquals("Ramen", label(1));
        assertTrue(card.getOptionView(1).createAccessibilityNodeInfo().isChecked());
        assertFalse(card.getOptionView(0).createAccessibilityNodeInfo().isChecked());

        // Tapping your own choice only shows results, so this proves the tap maps to the new poll's option id.
        other.pollShowResults = false;
        card.getOptionView(1).performClick();
        assertEquals(Boolean.TRUE, other.pollShowResults);
        assertFalse(other.pollSaving);
    }

    private String label(int index) {
        return ((TextView) card.getOptionView(index).findViewById(R.id.pollOptionLabel))
                .getText()
                .toString();
    }

    @Test
    public void questionHtmlEntitiesAreDecoded() {
        CommentPoll poll = PollHelperTest.makePoll(1, 1).question("Tom &amp; Jerry &lt;3");
        bind(poll);
        assertEquals("Tom & Jerry <3", text(R.id.pollQuestion).getText().toString());
    }
}
