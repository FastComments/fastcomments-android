package com.fastcomments.sdk;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

        questionView = findViewById(R.id.pollBuilderQuestion);
        optionsContainer = findViewById(R.id.pollBuilderOptions);
        addOptionView = findViewById(R.id.pollBuilderAddOption);
        durationsContainer = findViewById(R.id.pollBuilderDurations);
        requireVoteView = findViewById(R.id.pollBuilderRequireVote);
        privacyView = findViewById(R.id.pollBuilderPrivacy);
        errorView = findViewById(R.id.pollBuilderError);
        PollStyle.applyFooterGaps(findViewById(R.id.pollBuilderFooter));

        questionView.setFilters(createFilters(PollDraft.MAX_QUESTION_LENGTH));
        questionView.addTextChangedListener(new SimpleWatcher(text -> draft.question = text));
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

        for (int i = 0; i < PollDraft.CLOSES_DAYS.length; i++) {
            final int days = PollDraft.CLOSES_DAYS[i];
            TextView pill = new TextView(context);
            pill.setText(DURATION_LABELS[i]);
            pill.setTextSize(14);
            pill.setTextColor(getContext().getColorStateList(R.color.fastcomments_poll_length_text));
            pill.setBackgroundResource(R.drawable.fastcomments_poll_length_pill);
            pill.setPaddingRelative(dp(10), dp(3), dp(10), dp(3));
            pill.setOnClickListener(v -> {
                draft.closesDays = days;
                clearError();
                renderDurations();
            });
            durationsContainer.addView(pill);
        }

        requireVoteView.setButtonTintList(ColorStateList.valueOf(textColor));
        requireVoteView.setOnCheckedChangeListener((button, checked) -> {
            if (!binding) {
                draft.requireVoteToSeeResults = checked;
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
                    draft.privacy = position;
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

    public void bind(@NonNull PollDraft draft) {
        this.draft = draft;
        binding = true;
        questionView.setText(draft.question);
        requireVoteView.setChecked(draft.requireVoteToSeeResults);
        privacyView.setSelection(draft.privacy, false);
        binding = false;
        renderOptions();
        renderDurations();
        clearError();
    }

    public PollDraft getDraft() {
        return draft;
    }

    public void setOnRemoveListener(OnRemoveListener listener) {
        this.removeListener = listener;
    }

    public void showError(int stringRes) {
        errorView.setText(stringRes);
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
        if (draft.options.size() >= PollDraft.MAX_OPTIONS) {
            return;
        }
        draft.options.add("");
        clearError();
        renderOptions();
        if (focus) {
            getOptionInput(draft.options.size() - 1).requestFocus();
        }
    }

    private void removeOption(int index) {
        if (draft.options.size() <= PollDraft.MIN_OPTIONS) {
            return;
        }
        draft.options.remove(index);
        clearError();
        renderOptions();
        getOptionInput(Math.max(0, index - 1)).requestFocus();
    }

    private void renderOptions() {
        optionsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        final boolean canRemove = draft.options.size() > PollDraft.MIN_OPTIONS;
        for (int i = 0; i < draft.options.size(); i++) {
            final int index = i;
            View row = inflater.inflate(R.layout.fastcomments_poll_builder_option, optionsContainer, false);
            LayoutParams params = (LayoutParams) row.getLayoutParams();
            if (i > 0) {
                params.topMargin = dp(8);
            }
            setRowFocused(row, false);
            row.findViewById(R.id.pollBuilderOptionMark)
                    .setBackground(PollStyle.createShape(
                            getContext(),
                            PollStyle.MARK_RADIUS_DP,
                            color(R.color.fastcomments_poll_mark_bg),
                            color(R.color.fastcomments_poll_border),
                            false));

            EditText input = row.findViewById(R.id.pollBuilderOptionInput);
            input.setHint(getContext().getString(R.string.fastcomments_poll_option_placeholder, i + 1));
            input.setFilters(createFilters(PollDraft.MAX_OPTION_LENGTH));
            input.setText(draft.options.get(i));
            boolean isLast = i == draft.options.size() - 1;
            input.setImeOptions(
                    isLast && draft.options.size() >= PollDraft.MAX_OPTIONS
                            ? EditorInfo.IME_ACTION_DONE
                            : EditorInfo.IME_ACTION_NEXT);
            input.addTextChangedListener(new SimpleWatcher(text -> draft.options.set(index, text)));
            input.setOnFocusChangeListener((v, hasFocus) -> setRowFocused(row, hasFocus));
            input.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId != EditorInfo.IME_ACTION_NEXT) {
                    return false;
                }
                if (index + 1 < draft.options.size()) {
                    getOptionInput(index + 1).requestFocus();
                } else {
                    addOption(true);
                }
                return true;
            });
            input.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL
                        && event.getAction() == KeyEvent.ACTION_DOWN
                        && input.getText().length() == 0
                        && draft.options.size() > PollDraft.MIN_OPTIONS) {
                    removeOption(index);
                    return true;
                }
                return false;
            });

            View remove = row.findViewById(R.id.pollBuilderOptionRemove);
            remove.setVisibility(canRemove ? VISIBLE : INVISIBLE);
            remove.setOnClickListener(v -> removeOption(index));

            optionsContainer.addView(row, params);
        }
        addOptionView.setVisibility(draft.options.size() < PollDraft.MAX_OPTIONS ? VISIBLE : GONE);
    }

    private void renderDurations() {
        for (int i = 0; i < durationsContainer.getChildCount(); i++) {
            View pill = durationsContainer.getChildAt(i);
            boolean pressed = PollDraft.CLOSES_DAYS[i] == draft.closesDays;
            pill.setSelected(pressed);
        }
    }

    private void setRowFocused(View row, boolean focused) {
        row.setBackground(PollStyle.createShape(
                getContext(),
                PollStyle.OPTION_RADIUS_DP,
                color(R.color.fastcomments_poll_chip_bg),
                focused ? color(R.color.fastcomments_poll_accent) : color(android.R.color.transparent),
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
