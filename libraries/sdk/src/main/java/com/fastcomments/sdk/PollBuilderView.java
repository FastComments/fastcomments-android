package com.fastcomments.sdk;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.fastcomments.model.PollPrivacy;
import com.google.android.flexbox.FlexboxLayout;

/**
 * Writes a {@link PollDraft} inside the comment input. Mirrors the web widget's poll editing card.
 */
public class PollBuilderView extends LinearLayout {

    private static final int[] DURATION_LABELS = {
        R.string.fastcomments_poll_closes_never,
        R.string.fastcomments_poll_closes_1_day,
        R.string.fastcomments_poll_closes_3_days,
        R.string.fastcomments_poll_closes_1_week
    };
    private static final int[] PRIVACY_LABELS = {
        R.string.fastcomments_poll_privacy_anonymous,
        R.string.fastcomments_poll_privacy_admins,
        R.string.fastcomments_poll_privacy_public
    };

    public interface OnRemoveListener {
        void onRemove();
    }

    private final EditText questionView;
    private final LinearLayout optionsContainer;
    private final View addOptionView;
    private final FlexboxLayout durationsContainer;
    private final CheckBox requireVoteView;
    private final Spinner privacyView;
    private final TextView errorView;

    private PollDraft draft;
    private OnRemoveListener removeListener;
    private int accent;
    // Set while views are filled from the draft, so their listeners don't write the draft back.
    private boolean binding;

    public PollBuilderView(Context context) {
        this(context, null);
    }

    public PollBuilderView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        // The web builder's area padding: 4px 14px 12px.
        setPaddingRelative(dp(14), dp(4), dp(14), dp(12));
        LayoutInflater.from(context).inflate(R.layout.fastcomments_poll_builder, this, true);
        accent = color(R.color.fastcomments_poll_accent);

        questionView = findViewById(R.id.pollBuilderQuestion);
        optionsContainer = findViewById(R.id.pollBuilderOptions);
        addOptionView = findViewById(R.id.pollBuilderAddOption);
        durationsContainer = findViewById(R.id.pollBuilderDurations);
        requireVoteView = findViewById(R.id.pollBuilderRequireVote);
        privacyView = findViewById(R.id.pollBuilderPrivacy);
        errorView = findViewById(R.id.pollBuilderError);
        PollStyle.applyFooterGaps(findViewById(R.id.pollBuilderFooter));

        questionView.setFilters(createFilters(PollDraft.MAX_QUESTION_LENGTH));
        questionView.addTextChangedListener(new SimpleWatcher(text -> draft.setQuestion(text)));
        questionView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT && optionsContainer.getChildCount() > 0) {
                getOptionInput(0).requestFocus();
                return true;
            }
            return false;
        });

        int textColor = color(R.color.fastcomments_poll_text);
        addOptionView.setBackground(PollStyle.createShape(
                context,
                PollStyle.OPTION_RADIUS_DP,
                color(android.R.color.transparent),
                color(R.color.fastcomments_poll_border),
                true));
        findViewById(R.id.pollBuilderAddOptionMark)
                .setBackground(PollStyle.createShape(
                        context,
                        PollStyle.MARK_RADIUS_DP,
                        color(android.R.color.transparent),
                        color(R.color.fastcomments_poll_border),
                        true));
        addOptionView.setOnClickListener(v -> addOption(true));

        for (final PollDraft.Duration duration : PollDraft.Duration.values()) {
            TextView pill = new TextView(context);
            pill.setText(DURATION_LABELS[duration.ordinal()]);
            pill.setTextSize(14);
            pill.setPaddingRelative(dp(10), 0, dp(10), 0);
            pill.setGravity(Gravity.CENTER);
            pill.setMinHeight(dp(48));
            pill.setMinWidth(dp(48));
            pill.setOnClickListener(v -> {
                draft.setDuration(duration);
                clearError();
                renderDurations();
            });
            durationsContainer.addView(pill);
        }
        applyAccent();

        requireVoteView.setButtonTintList(ColorStateList.valueOf(textColor));
        requireVoteView.setOnCheckedChangeListener((button, checked) -> {
            if (!binding) {
                draft.setRequireVoteToSeeResults(checked);
                clearError();
            }
        });

        String[] privacyLabels = new String[PRIVACY_LABELS.length];
        for (int i = 0; i < PRIVACY_LABELS.length; i++) {
            privacyLabels[i] = context.getString(PRIVACY_LABELS[i]);
        }
        ArrayAdapter<String> privacyAdapter =
                new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, privacyLabels) {
                    @NonNull
                    @Override
                    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                        View view = super.getView(position, convertView, parent);
                        if (view instanceof TextView) {
                            ((TextView) view).setTextSize(14);
                            ((TextView) view).setTextColor(textColor);
                            view.setPadding(0, 0, 0, 0);
                        }
                        return view;
                    }
                };
        privacyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        privacyView.setAdapter(privacyAdapter);
        privacyView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!binding && draft != null) {
                    // The labels are listed in PollPrivacy value order.
                    draft.setPrivacy(PollPrivacy.fromValue(position));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // The web shows this as a hover tooltip; a tap shows the same tooltip here.
        View help = findViewById(R.id.pollBuilderPrivacyHelp);
        help.setOnClickListener(View::performLongClick);

        TextView removeView = findViewById(R.id.pollBuilderRemove);
        removeView.setPaintFlags(removeView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        removeView.setOnClickListener(v -> {
            if (removeListener != null) {
                removeListener.onRemove();
            }
        });
    }

    void bind(@NonNull PollDraft draft) {
        this.draft = draft;
        binding = true;
        questionView.setText(draft.getQuestion());
        requireVoteView.setChecked(draft.isRequireVoteToSeeResults());
        privacyView.setSelection(draft.getPrivacy().getValue(), false);
        binding = false;
        renderOptions();
        renderDurations();
        clearError();
    }

    // Option rows share view ids, so the framework would restore one row's text into all of them. The owning input
    // view saves the draft instead.
    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        dispatchFreezeSelfOnly(container);
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        dispatchThawSelfOnly(container);
    }

    /**
     * The poll accent from the SDK's runtime theme: the chosen poll length, the question underline and the focused
     * option's border.
     */
    public void setAccentColor(@ColorInt int accent) {
        if (this.accent == accent) {
            return;
        }
        this.accent = accent;
        applyAccent();
        for (int i = 0; i < optionsContainer.getChildCount(); i++) {
            setRowFocused(optionsContainer.getChildAt(i), getOptionInput(i).hasFocus());
        }
    }

    private void applyAccent() {
        // The web's 2px underline under the borderless question while it has focus.
        GradientDrawable underline = new GradientDrawable();
        underline.setColor(accent);
        LayerDrawable focusedQuestion = new LayerDrawable(new Drawable[] {underline});
        focusedQuestion.setLayerGravity(0, Gravity.BOTTOM);
        focusedQuestion.setLayerHeight(0, dp(2));
        StateListDrawable question = new StateListDrawable();
        question.addState(new int[] {android.R.attr.state_focused}, focusedQuestion);
        question.addState(new int[] {}, new ColorDrawable(Color.TRANSPARENT));
        questionView.setBackground(question);

        ColorStateList pillText = new ColorStateList(
                new int[][] {new int[] {android.R.attr.state_selected}, new int[] {}},
                new int[] {color(R.color.fastcomments_poll_accent_text), color(R.color.fastcomments_poll_text)});
        for (int i = 0; i < durationsContainer.getChildCount(); i++) {
            TextView pill = (TextView) durationsContainer.getChildAt(i);
            // Outlined pill, filled with the accent once chosen.
            StateListDrawable background = new StateListDrawable();
            background.addState(new int[] {android.R.attr.state_selected}, createPill(accent, accent));
            background.addState(new int[] {}, createPill(Color.TRANSPARENT, color(R.color.fastcomments_poll_border)));
            // The tap target is 48dp tall; the drawn pill keeps the web's 3dp above and below the text.
            int inset = Math.max(0, (dp(48) - pill.getLineHeight() - dp(6)) / 2);
            pill.setBackground(new InsetDrawable(background, 0, inset, 0, inset));
            pill.setTextColor(pillText);
        }
    }

    private GradientDrawable createPill(int fill, int stroke) {
        GradientDrawable pill = new GradientDrawable();
        pill.setCornerRadius(dp(999));
        pill.setColor(fill);
        pill.setStroke(dp(1), stroke);
        return pill;
    }

    public void setOnRemoveListener(OnRemoveListener listener) {
        this.removeListener = listener;
    }

    void showError(@NonNull PollDraft.Error error) {
        errorView.setText(error.message);
        errorView.setVisibility(VISIBLE);
        // The builder scrolls inside the composer, so bring the error into view or it can go unseen.
        errorView.post(() ->
                errorView.requestRectangleOnScreen(new Rect(0, 0, errorView.getWidth(), errorView.getHeight()), false));
    }

    public void focusQuestion() {
        questionView.requestFocus();
    }

    private void clearError() {
        errorView.setVisibility(GONE);
    }

    private void addOption(boolean focus) {
        if (!draft.addOption()) {
            return;
        }
        clearError();
        optionsContainer.addView(createOptionRow(""));
        updateOptionRows();
        if (focus) {
            getOptionInput(draft.getOptions().size() - 1).requestFocus();
        }
    }

    private void removeOption(int index) {
        if (index < 0 || !draft.removeOption(index)) {
            return;
        }
        clearError();
        optionsContainer.removeViewAt(index);
        updateOptionRows();
        EditText previous = getOptionInput(Math.max(0, index - 1));
        previous.requestFocus();
        previous.setSelection(previous.length());
    }

    private void renderOptions() {
        optionsContainer.removeAllViews();
        for (String option : draft.getOptions()) {
            optionsContainer.addView(createOptionRow(option));
        }
        updateOptionRows();
    }

    // Rows look up their position when used, so adding or removing one leaves the others, and the keyboard, alone.
    private View createOptionRow(String text) {
        final View row = LayoutInflater.from(getContext())
                .inflate(R.layout.fastcomments_poll_builder_option, optionsContainer, false);
        setRowFocused(row, false);
        row.findViewById(R.id.pollBuilderOptionMark)
                .setBackground(PollStyle.createShape(
                        getContext(),
                        PollStyle.MARK_RADIUS_DP,
                        color(R.color.fastcomments_poll_mark_bg),
                        color(R.color.fastcomments_poll_border),
                        false));

        EditText input = row.findViewById(R.id.pollBuilderOptionInput);
        input.setFilters(createFilters(PollDraft.MAX_OPTION_LENGTH));
        input.setText(text);
        input.addTextChangedListener(new SimpleWatcher(value -> {
            int index = optionsContainer.indexOfChild(row);
            // A removed row can still get a late edit from the keyboard.
            if (index >= 0) {
                draft.setOption(index, value);
            }
        }));
        input.setOnFocusChangeListener((v, hasFocus) -> setRowFocused(row, hasFocus));
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_NEXT) {
                return false;
            }
            int index = optionsContainer.indexOfChild(row);
            if (index < 0) {
                return true;
            }
            if (index + 1 < draft.getOptions().size()) {
                getOptionInput(index + 1).requestFocus();
            } else {
                addOption(true);
            }
            return true;
        });
        row.findViewById(R.id.pollBuilderOptionRemove)
                .setOnClickListener(v -> removeOption(optionsContainer.indexOfChild(row)));
        return row;
    }

    private void updateOptionRows() {
        int count = optionsContainer.getChildCount();
        boolean canRemove = count > PollDraft.MIN_OPTIONS;
        for (int i = 0; i < count; i++) {
            View row = optionsContainer.getChildAt(i);
            LayoutParams params = (LayoutParams) row.getLayoutParams();
            int topMargin = i > 0 ? dp(8) : 0;
            if (params.topMargin != topMargin) {
                params.topMargin = topMargin;
                row.setLayoutParams(params);
            }
            row.findViewById(R.id.pollBuilderOptionRemove).setVisibility(canRemove ? VISIBLE : INVISIBLE);

            EditText input = row.findViewById(R.id.pollBuilderOptionInput);
            input.setHint(getContext().getString(R.string.fastcomments_poll_option_placeholder, i + 1));
            int imeAction = i == count - 1 && count >= PollDraft.MAX_OPTIONS
                    ? EditorInfo.IME_ACTION_DONE
                    : EditorInfo.IME_ACTION_NEXT;
            if (input.getImeOptions() != imeAction) {
                input.setImeOptions(imeAction);
                if (input.isFocused()) {
                    InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
                    if (imm != null) {
                        imm.restartInput(input);
                    }
                }
            }
        }
        addOptionView.setVisibility(count < PollDraft.MAX_OPTIONS ? VISIBLE : GONE);
    }

    private void renderDurations() {
        for (int i = 0; i < durationsContainer.getChildCount(); i++) {
            durationsContainer.getChildAt(i).setSelected(draft.getDuration().ordinal() == i);
        }
    }

    private void setRowFocused(View row, boolean focused) {
        row.setBackground(PollStyle.createShape(
                getContext(),
                PollStyle.OPTION_RADIUS_DP,
                color(R.color.fastcomments_poll_chip_bg),
                focused ? accent : color(android.R.color.transparent),
                false));
    }

    private EditText getOptionInput(int index) {
        return optionsContainer.getChildAt(index).findViewById(R.id.pollBuilderOptionInput);
    }

    /**
     * Pasted text is flattened to one line, like the web builder's plain-text paste.
     */
    private static InputFilter[] createFilters(int maxLength) {
        InputFilter collapse = (source, start, end, dest, dstart, dend) -> {
            CharSequence inserted = source.subSequence(start, end);
            for (int i = 0; i < inserted.length(); i++) {
                char c = inserted.charAt(i);
                if (Character.isWhitespace(c) && c != ' ') {
                    return inserted.toString().replaceAll("\\s+", " ");
                }
            }
            return null;
        };
        return new InputFilter[] {collapse, new InputFilter.LengthFilter(maxLength)};
    }

    private int color(int resId) {
        return getContext().getColor(resId);
    }

    private int dp(int value) {
        return PollStyle.dp(getContext(), value);
    }

    private interface TextSink {
        void accept(String text);
    }

    private final class SimpleWatcher implements TextWatcher {
        private final TextSink sink;

        SimpleWatcher(TextSink sink) {
            this.sink = sink;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            if (binding || draft == null) {
                return;
            }
            sink.accept(s.toString());
            clearError();
        }
    }
}
