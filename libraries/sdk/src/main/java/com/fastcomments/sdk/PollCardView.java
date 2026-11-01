package com.fastcomments.sdk;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.Html;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.fastcomments.model.APIError;
import com.fastcomments.model.CommentPoll;
import com.fastcomments.model.CommentPollOption;
import com.fastcomments.model.PollPrivacy;
import com.fastcomments.model.PublicComment;
import com.fastcomments.model.VotePollResponse;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Renders a comment's poll the same way the web widget's polls extension does: each option is a chip that doubles
 * as its own result bar once results are showing.
 */
public class PollCardView extends LinearLayout {

    private static final long BAR_ANIMATION_MS = 300;

    private final TextView questionView;
    private final LinearLayout optionsContainer;
    private final TextView totalView;
    private final TextView privacyView;
    private final TextView closesView;
    private final TextView errorView;
    private final View savedView;
    private final TextView changeVoteView;
    private final TextView toggleResultsView;
    private final TextView votersView;

    private final List<View> optionViews = new ArrayList<>();
    private final List<String> optionIds = new ArrayList<>();
    // The raw labels last rendered, so a rebind only re-parses HTML that changed.
    private final List<String> optionLabels = new ArrayList<>();

    // Shared by every option and every bind; recycling cards while scrolling shouldn't allocate them again.
    private final ColorStateList votableChipColors;
    private final ColorStateList readOnlyChipColors;
    private final NumberFormat numberFormat = NumberFormat.getInstance();
    private final DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.SHORT);
    private final AccessibilityDelegate optionAccessibility = new AccessibilityDelegate() {
        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName(RadioButton.class.getName());
            info.setCheckable(true);
            info.setChecked(host.isSelected());
        }
    };

    private FastCommentsSDK sdk;
    private RenderableComment comment;
    private boolean showingResults;
    private OffsetDateTime boundClosesAt;
    private String boundQuestion;

    public PollCardView(Context context) {
        this(context, null);
    }

    public PollCardView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        // The web card sits 12px under the comment text and 2px in from its start edge.
        setPaddingRelative(dp(2), dp(12), 0, 0);
        LayoutInflater.from(context).inflate(R.layout.fastcomments_poll_card, this, true);
        votableChipColors = new ColorStateList(
                new int[][] {new int[] {android.R.attr.state_pressed}, new int[] {}},
                new int[] {color(R.color.fastcomments_poll_chip_bg_pressed), color(R.color.fastcomments_poll_chip_bg)});
        readOnlyChipColors = ColorStateList.valueOf(color(R.color.fastcomments_poll_chip_bg));

        questionView = findViewById(R.id.pollQuestion);
        optionsContainer = findViewById(R.id.pollOptions);
        totalView = findViewById(R.id.pollTotal);
        privacyView = findViewById(R.id.pollPrivacy);
        closesView = findViewById(R.id.pollCloses);
        errorView = findViewById(R.id.pollError);
        savedView = findViewById(R.id.pollSaved);
        changeVoteView = findViewById(R.id.pollChangeVote);
        toggleResultsView = findViewById(R.id.pollToggleResults);
        votersView = findViewById(R.id.pollVoters);
        PollStyle.applyFooterGaps(findViewById(R.id.pollFooter));

        for (TextView link : new TextView[] {changeVoteView, toggleResultsView, votersView}) {
            link.setPaintFlags(link.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        }

        changeVoteView.setOnClickListener(v -> {
            comment.pollShowResults = false;
            refresh();
        });
        toggleResultsView.setOnClickListener(v -> {
            comment.pollShowResults = !PollHelper.isShowingResults(comment);
            refresh();
        });
        votersView.setOnClickListener(v ->
                new PollVotersDialog(getContext(), sdk, comment.getComment().getId()).show());
    }

    public void bind(@NonNull FastCommentsSDK sdk, @NonNull RenderableComment comment) {
        final PublicComment publicComment = comment.getComment();
        final CommentPoll poll = publicComment.getPoll();
        final List<CommentPollOption> options = poll.getOptions() != null ? poll.getOptions() : new ArrayList<>();
        final boolean sameOptions = this.comment == comment && hasOptionIds(options);
        final boolean wasShowingResults = showingResults;
        this.sdk = sdk;
        this.comment = comment;

        final boolean closed = PollHelper.isClosed(poll);
        final boolean voted = PollHelper.hasVoted(publicComment);
        final boolean locked = PollHelper.resultsLocked(publicComment);
        final boolean results = PollHelper.isShowingResults(comment);
        final boolean votable = PollHelper.canVote(sdk.getConfig(), sdk.getCurrentUser(), poll);
        final int total = poll.getTotalVotes() != null ? poll.getTotalVotes() : 0;

        if (!Objects.equals(boundQuestion, poll.getQuestion())) {
            questionView.setText(htmlToText(poll.getQuestion()));
            boundQuestion = poll.getQuestion();
        }

        if (!sameOptions) {
            syncOptionViews(options);
        }
        // Only a live tally change on a card already showing results slides the bars, like the web's width transition.
        final boolean animate = sameOptions && wasShowingResults && results && ValueAnimator.areAnimatorsEnabled();
        for (int i = 0; i < options.size(); i++) {
            CommentPollOption option = options.get(i);
            bindOption(
                    i,
                    option,
                    PollHelper.isSelected(publicComment, option.getId()),
                    PollHelper.getPercent(option.getVotes(), total),
                    results,
                    votable,
                    closed,
                    animate);
        }
        optionsContainer.setAlpha(comment.pollSaving ? 0.6f : 1f);

        totalView.setText(getResponsesText(total));
        privacyView.setText(getPrivacyText(poll));
        if (poll.getClosesAt() == null) {
            closesView.setVisibility(GONE);
        } else {
            closesView.setVisibility(VISIBLE);
            closesView.setText(
                    closed
                            ? getContext().getString(R.string.fastcomments_poll_closed)
                            : getContext()
                                    .getString(
                                            R.string.fastcomments_poll_closes_at,
                                            dateFormat.format(
                                                    Date.from(poll.getClosesAt().toInstant()))));
        }
        errorView.setVisibility(comment.pollError != null ? VISIBLE : GONE);
        errorView.setText(comment.pollError);
        savedView.setVisibility(voted ? VISIBLE : GONE);
        changeVoteView.setVisibility(voted && results && !closed ? VISIBLE : GONE);
        // The web shows this toggle on locked polls too, where it can't reveal anything; it is left out here.
        toggleResultsView.setVisibility(!voted && !closed && !locked ? VISIBLE : GONE);
        toggleResultsView.setText(
                results ? R.string.fastcomments_poll_hide_results : R.string.fastcomments_poll_show_results);
        votersView.setVisibility(PollHelper.canSeeVoters(poll) && total > 0 ? VISIBLE : GONE);

        showingResults = results;
        boundClosesAt = poll.getClosesAt();
    }

    /**
     * Apply a live tally change. Like the web's patchPollCounts this only moves the numbers, falling back to a full
     * bind when the options or close date changed.
     */
    public void patchCounts(@NonNull FastCommentsSDK sdk, @NonNull RenderableComment comment) {
        final CommentPoll poll = comment.getComment().getPoll();
        final List<CommentPollOption> options = poll.getOptions() != null ? poll.getOptions() : new ArrayList<>();
        if (this.comment != comment || !hasOptionIds(options) || !Objects.equals(boundClosesAt, poll.getClosesAt())) {
            bind(sdk, comment);
            return;
        }
        final int total = poll.getTotalVotes() != null ? poll.getTotalVotes() : 0;
        final boolean animate = showingResults && ValueAnimator.areAnimatorsEnabled();
        for (int i = 0; i < options.size(); i++) {
            final View optionView = optionViews.get(i);
            final int pct = PollHelper.getPercent(options.get(i).getVotes(), total);
            final String pctText = getContext().getString(R.string.fastcomments_poll_percent, pct);
            ((TextView) optionView.findViewById(R.id.pollOptionPct)).setText(pctText);
            setBarLevel(optionView, getBar(optionView), showingResults ? pct * 100 : 0, animate);
            final CharSequence label = ((TextView) optionView.findViewById(R.id.pollOptionLabel)).getText();
            optionView.setContentDescription(showingResults ? label + ", " + pctText : label);
        }
        totalView.setText(getResponsesText(total));
        votersView.setVisibility(PollHelper.canSeeVoters(poll) && total > 0 ? VISIBLE : GONE);
    }

    int getOptionCount() {
        return optionViews.size();
    }

    View getOptionView(int index) {
        return optionViews.get(index);
    }

    int getOptionBarLevel(int index) {
        return getBar(optionViews.get(index)).getLevel();
    }

    private void refresh() {
        bind(sdk, comment);
    }

    private boolean hasOptionIds(List<CommentPollOption> options) {
        if (options.size() != optionIds.size()) {
            return false;
        }
        for (int i = 0; i < options.size(); i++) {
            if (!optionIds.get(i).equals(options.get(i).getId())) {
                return false;
            }
        }
        return true;
    }

    // A recycled card keeps its option views and only adds or drops the difference, so scrolling doesn't re-inflate.
    private void syncOptionViews(List<CommentPollOption> options) {
        while (optionViews.size() > options.size()) {
            final int last = optionViews.size() - 1;
            optionsContainer.removeView(optionViews.remove(last));
            optionLabels.remove(last);
        }
        LayoutInflater inflater = LayoutInflater.from(getContext());
        while (optionViews.size() < options.size()) {
            final int index = optionViews.size();
            View optionView = inflater.inflate(R.layout.fastcomments_poll_option, optionsContainer, false);
            LayoutParams params = (LayoutParams) optionView.getLayoutParams();
            if (index > 0) {
                params.topMargin = dp(8);
            }
            optionView.setBackground(createOptionBackground());
            optionView
                    .findViewById(R.id.pollOptionMark)
                    .setBackground(PollStyle.createShape(
                            getContext(),
                            PollStyle.MARK_RADIUS_DP,
                            color(R.color.fastcomments_poll_mark_bg),
                            color(R.color.fastcomments_poll_border),
                            false));
            ((ImageView) optionView.findViewById(R.id.pollOptionCheck))
                    .setImageTintList(ColorStateList.valueOf(color(R.color.fastcomments_poll_accent_text)));
            optionView.setAccessibilityDelegate(optionAccessibility);
            // Read the id at tap time, since this view can be rebound to another poll.
            optionView.setOnClickListener(v -> onOptionClick(optionIds.get(index)));
            optionsContainer.addView(optionView, params);
            optionViews.add(optionView);
            optionLabels.add(null);
        }
        optionIds.clear();
        for (CommentPollOption option : options) {
            optionIds.add(option.getId());
        }
    }

    private void bindOption(
            int index,
            CommentPollOption option,
            boolean selected,
            int pct,
            boolean results,
            boolean votable,
            boolean closed,
            boolean animate) {
        final View optionView = optionViews.get(index);
        final LayerDrawable background = (LayerDrawable) optionView.getBackground();
        final GradientDrawable track = (GradientDrawable) background.getDrawable(0);
        final ClipDrawable bar = getBar(optionView);
        final int accent = color(R.color.fastcomments_poll_accent);

        final TextView labelView = optionView.findViewById(R.id.pollOptionLabel);
        if (!Objects.equals(optionLabels.get(index), option.getLabel())) {
            labelView.setText(htmlToText(option.getLabel()));
            optionLabels.set(index, option.getLabel());
        }
        final CharSequence label = labelView.getText();
        TextView pctView = optionView.findViewById(R.id.pollOptionPct);
        String pctText = getContext().getString(R.string.fastcomments_poll_percent, pct);
        pctView.setText(pctText);
        pctView.setAlpha(results ? 1f : 0f);

        track.setStroke(dp(1), selected ? accent : color(android.R.color.transparent));
        track.setColor(votable ? votableChipColors : readOnlyChipColors);
        ((GradientDrawable) bar.getDrawable())
                .setColor(color(
                        results && selected
                                ? R.color.fastcomments_poll_bar_selected
                                : R.color.fastcomments_poll_bar_track));
        setBarLevel(optionView, bar, results ? pct * 100 : 0, animate);

        GradientDrawable mark =
                (GradientDrawable) optionView.findViewById(R.id.pollOptionMark).getBackground();
        mark.setColor(selected ? accent : color(R.color.fastcomments_poll_mark_bg));
        mark.setStroke(dp(1), selected ? accent : color(R.color.fastcomments_poll_border));
        optionView.findViewById(R.id.pollOptionCheck).setVisibility(selected ? VISIBLE : GONE);

        // The shared accessibility delegate reports this as the radio button's checked state.
        optionView.setSelected(selected);
        // A logged-out viewer can still tap, so the card can say why the vote didn't count.
        optionView.setEnabled(!closed);
        optionView.setContentDescription(results ? label + ", " + pctText : label);
    }

    private void onOptionClick(String optionId) {
        final RenderableComment target = comment;
        final PublicComment publicComment = target.getComment();
        final CommentPoll poll = publicComment.getPoll();
        if (target.pollSaving || PollHelper.isClosed(poll)) {
            return;
        }
        if (!PollHelper.canVote(sdk.getConfig(), sdk.getCurrentUser(), poll)) {
            target.pollError = getContext().getString(R.string.fastcomments_poll_login_to_vote);
            refresh();
            return;
        }
        target.pollError = null;
        if (PollHelper.isSelected(publicComment, optionId)) {
            target.pollShowResults = true;
            refresh();
            return;
        }
        target.pollSaving = true;
        refresh();
        sdk.votePoll(publicComment.getId(), optionId, new FCCallback<VotePollResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                target.pollSaving = false;
                target.pollError = getVoteErrorText(error);
                refreshIfBound(target);
                return CONSUME;
            }

            @Override
            public boolean onSuccess(VotePollResponse response) {
                target.pollSaving = false;
                target.pollShowResults = true;
                refreshIfBound(target);
                return CONSUME;
            }
        });
    }

    // The holder may have been recycled for another comment while the vote was in flight.
    private void refreshIfBound(RenderableComment target) {
        if (comment == target) {
            refresh();
        }
    }

    private String getVoteErrorText(APIError error) {
        if (error.getTranslatedError() != null && !error.getTranslatedError().isEmpty()) {
            return error.getTranslatedError();
        }
        if ("poll-login-required".equals(error.getCode())) {
            return getContext().getString(R.string.fastcomments_poll_login_to_vote);
        }
        if ("poll-closed".equals(error.getCode())) {
            return getContext().getString(R.string.fastcomments_poll_closed);
        }
        return getContext().getString(R.string.fastcomments_poll_vote_failed);
    }

    private String getResponsesText(int total) {
        return getResources()
                .getQuantityString(R.plurals.fastcomments_poll_responses, total, numberFormat.format(total));
    }

    private String getPrivacyText(CommentPoll poll) {
        if (poll.getPrivacy() == PollPrivacy.NUMBER_2) {
            return getContext().getString(R.string.fastcomments_poll_votes_public_visible);
        }
        if (poll.getPrivacy() == PollPrivacy.NUMBER_1) {
            return getContext().getString(R.string.fastcomments_poll_votes_admins_visible);
        }
        return getContext().getString(R.string.fastcomments_poll_votes_anonymous);
    }

    private Drawable createOptionBackground() {
        final int radius = dp(PollStyle.OPTION_RADIUS_DP);
        final int border = dp(1);
        GradientDrawable track = new GradientDrawable();
        track.setCornerRadii(PollStyle.getCornerRadii(getContext(), radius));
        GradientDrawable barShape = new GradientDrawable();
        barShape.setCornerRadii(PollStyle.getCornerRadii(getContext(), radius - border));
        ClipDrawable bar = new ClipDrawable(barShape, Gravity.START, ClipDrawable.HORIZONTAL);
        LayerDrawable layers = new LayerDrawable(new Drawable[] {track, bar});
        // The bar fills the chip inside its border, like the web's absolutely positioned bar.
        layers.setLayerInset(1, border, border, border, border);
        return layers;
    }

    private static ClipDrawable getBar(View optionView) {
        return (ClipDrawable) ((LayerDrawable) optionView.getBackground()).getDrawable(1);
    }

    private static void setBarLevel(View optionView, ClipDrawable bar, int level, boolean animate) {
        Object running = optionView.getTag();
        if (running instanceof ValueAnimator) {
            ((ValueAnimator) running).cancel();
            optionView.setTag(null);
        }
        if (!animate || bar.getLevel() == level) {
            bar.setLevel(level);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofInt(bar.getLevel(), level);
        animator.setDuration(BAR_ANIMATION_MS);
        animator.addUpdateListener(animation -> {
            Object value = animation.getAnimatedValue();
            if (value instanceof Integer) {
                bar.setLevel((Integer) value);
            }
        });
        optionView.setTag(animator);
        animator.start();
    }

    private static String htmlToText(String html) {
        if (html == null) {
            return "";
        }
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString();
    }

    private int color(int resId) {
        return getContext().getColor(resId);
    }

    private int dp(int value) {
        return PollStyle.dp(getContext(), value);
    }
}
