package com.fastcomments.sdk;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class FastCommentsView extends LinearLayout {

    private RecyclerView recyclerView;
    private CommentsAdapter adapter;
    private int currentPage = 1;

    public FastCommentsView(Context context) {
        super(context);
        init(context);
    }

    public FastCommentsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FastCommentsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
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
        FastCommentsSDK.init(getContext()).fetchComments(page, new FastCommentsSDK.CommentsCallback() {
            @Override
            public void onCommentsFetched(List<Comment> comments) {
                adapter.setComments(comments);
            }
        });
    }

    // Public method to load a specific page.
    public void goToPage(int page) {
        currentPage = page;
        loadComments(currentPage);
    }
}
