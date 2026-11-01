package com.fastcomments.sdk;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.fastcomments.model.APIError;
import com.fastcomments.model.GetPollVoterNamesSuccessResponse;
import com.fastcomments.model.PollVoterEntry;
import java.text.NumberFormat;

/**
 * "Who voted" for a poll whose privacy names its voters. Mirrors the web widget's voters modal.
 */
public class PollVotersDialog extends Dialog {

    // The search scans joined names server side, so typing a whole name should be one request. The keyboard's
    // search key skips the wait.
    private static final long SEARCH_DEBOUNCE_MS = 3000;

    private final FastCommentsSDK sdk;
    private final String commentId;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable debouncedSearch = this::startSearch;

    private EditText searchView;
    private LinearLayout listView;
    private TextView statusView;
    private View pagerView;
    private TextView prevView;
    private TextView nextView;
    private TextView countView;

    private int page = 0;
    private String search = "";
    // Every fetch claims an id so a slow earlier page cannot overwrite a newer one when it lands.
    private int requestId = 0;
    // Pager taps wait for the page in flight, so quick taps don't fire a request each.
    private boolean loading = false;

    public PollVotersDialog(@NonNull Context context, @NonNull FastCommentsSDK sdk, @NonNull String commentId) {
        super(context);
        this.sdk = sdk;
        this.commentId = commentId;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.fastcomments_poll_voters_dialog);

        searchView = findViewById(R.id.pollVotersSearch);
        listView = findViewById(R.id.pollVotersList);
        statusView = findViewById(R.id.pollVotersStatus);
        pagerView = findViewById(R.id.pollVotersPager);
        prevView = findViewById(R.id.pollVotersPrev);
        nextView = findViewById(R.id.pollVotersNext);
        countView = findViewById(R.id.pollVotersCount);

        for (TextView link : new TextView[] {prevView, nextView}) {
            link.setPaintFlags(link.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        }
        findViewById(R.id.pollVotersClose).setOnClickListener(v -> dismiss());
        prevView.setOnClickListener(v -> {
            if (!loading && page > 0) {
                fetchVoters(page - 1);
            }
        });
        nextView.setOnClickListener(v -> {
            if (!loading) {
                fetchVoters(page + 1);
            }
        });
        searchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(debouncedSearch);
                handler.postDelayed(debouncedSearch, SEARCH_DEBOUNCE_MS);
            }
        });
        searchView.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                handler.removeCallbacks(debouncedSearch);
                startSearch();
                return true;
            }
            return false;
        });

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setDimAmount(0.45f);
            int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
            int screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
            window.setLayout(Math.min(dp(420), screenWidth - dp(32)), WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            // The dialog stops at min(560dp, screen - 64dp) like the web modal; the list scrolls inside it.
            PollScrollView scroll = findViewById(R.id.pollVotersScroll);
            scroll.setMaxHeight(Math.min(dp(560), screenHeight - dp(64)) - dp(170));
        }

        fetchVoters(0);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(debouncedSearch);
        // Invalidate any fetch still in flight.
        requestId++;
        loading = false;
        super.onStop();
    }

    private void startSearch() {
        String value = searchView.getText() != null ? searchView.getText().toString() : "";
        if (value.equals(search)) {
            return;
        }
        search = value;
        // A new search starts over rather than landing on a page that may not exist.
        page = 0;
        fetchVoters(0);
    }

    // The page only advances once it arrives, so a failed "Next" retries the same page.
    private void fetchVoters(final int targetPage) {
        final int id = ++requestId;
        loading = true;
        showStatus(R.string.fastcomments_poll_voters_loading);
        sdk.getPollVoters(commentId, targetPage, search, new FCCallback<GetPollVoterNamesSuccessResponse>() {
            @Override
            public boolean onFailure(APIError error) {
                if (id == requestId) {
                    loading = false;
                    showStatus(R.string.fastcomments_poll_voters_failed);
                }
                return CONSUME;
            }

            @Override
            public boolean onSuccess(GetPollVoterNamesSuccessResponse response) {
                if (id == requestId) {
                    loading = false;
                    page = targetPage;
                    renderPage(response);
                }
                return CONSUME;
            }
        });
    }

    private void showStatus(int stringRes) {
        listView.removeAllViews();
        statusView.setText(stringRes);
        statusView.setVisibility(View.VISIBLE);
    }

    private void renderPage(GetPollVoterNamesSuccessResponse response) {
        if (response.getVoters() == null || response.getVoters().isEmpty()) {
            showStatus(R.string.fastcomments_poll_voters_none);
            return;
        }
        statusView.setVisibility(View.GONE);
        listView.removeAllViews();
        for (int i = 0; i < response.getVoters().size(); i++) {
            listView.addView(createRow(response.getVoters().get(i), i > 0));
        }

        int currentPage = response.getPage() != null ? response.getPage() : 0;
        int total = response.getTotal() != null ? response.getTotal() : 0;
        pagerView.setVisibility(View.VISIBLE);
        setLinkEnabled(prevView, currentPage > 0);
        setLinkEnabled(nextView, Boolean.TRUE.equals(response.getHasMore()));
        countView.setText(getContext()
                .getResources()
                .getQuantityString(
                        R.plurals.fastcomments_poll_voters_count,
                        total,
                        NumberFormat.getInstance().format(total)));
    }

    private View createRow(PollVoterEntry voter, boolean divided) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(7), 0, dp(7));

        TextView name = new TextView(getContext());
        name.setText(voter.getName());
        name.setTextSize(14);
        name.setTextColor(getContext().getColor(R.color.fastcomments_poll_chip_text));
        row.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView choice = new TextView(getContext());
        // Option labels are stored entity-escaped, like on the poll card.
        choice.setText(
                voter.getOptionLabel() != null
                        ? Html.fromHtml(voter.getOptionLabel(), Html.FROM_HTML_MODE_LEGACY)
                                .toString()
                        : "");
        choice.setTextSize(14);
        choice.setTextColor(getContext().getColor(R.color.fastcomments_poll_text_muted));
        choice.setGravity(Gravity.END);
        choice.setMaxWidth(Math.round(getWindowWidth() * 0.55f));
        LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        choiceParams.setMarginStart(dp(12));
        row.addView(choice, choiceParams);

        if (!divided) {
            return row;
        }
        LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        View divider = new View(getContext());
        divider.setBackgroundColor(getContext().getColor(R.color.fastcomments_poll_border));
        wrapper.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        wrapper.addView(row);
        return wrapper;
    }

    private static void setLinkEnabled(TextView link, boolean enabled) {
        link.setEnabled(enabled);
        link.setAlpha(enabled ? 1f : 0.5f);
        link.setPaintFlags(
                enabled
                        ? link.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG
                        : link.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
    }

    private int getWindowWidth() {
        Window window = getWindow();
        if (window != null && window.getAttributes().width > 0) {
            return window.getAttributes().width;
        }
        return getContext().getResources().getDisplayMetrics().widthPixels;
    }

    private int dp(int value) {
        return PollStyle.dp(getContext(), value);
    }
}
