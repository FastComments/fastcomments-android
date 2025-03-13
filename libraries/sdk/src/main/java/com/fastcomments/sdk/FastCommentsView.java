package com.fastcomments.sdk;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fastcomments.client.R;
import com.fastcomments.core.CommentWidgetConfig;

import java.util.List;

public class FastCommentsView extends LinearLayout {

    private RecyclerView recyclerView;
    private CommentsAdapter adapter;
    private CommentWidgetConfig config;
    private int currentPage = 1;

    public FastCommentsView(Context context, CommentWidgetConfig config) {
        super(context);
        this.config = config;
        init(context);
    }

    public FastCommentsView(Context context, AttributeSet attrs, CommentWidgetConfig config) {
        super(context, attrs);
        this.config = config;
        init(context);
    }

    public FastCommentsView(Context context, AttributeSet attrs, int defStyleAttr, CommentWidgetConfig config) {
        super(context, attrs, defStyleAttr);
        this.config = config;
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.fast_comments_view, this, true);
        recyclerView = findViewById(R.id.recyclerViewComments);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new CommentsAdapter();
        recyclerView.setAdapter(adapter);

        // Load the first page on initialization.
        loadComments(currentPage);
    }

    private void loadComments(int page) {
        new FastCommentsSDK(getContext()).loadForConfig(config);
    }

    // Public method to load a specific page.
    public void goToPage(int page) {
        currentPage = page;
        loadComments(currentPage);
    }
}
