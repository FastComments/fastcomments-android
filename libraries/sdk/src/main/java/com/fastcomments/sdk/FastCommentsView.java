package com.fastcomments.sdk;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fastcomments.model.APIError;
import com.fastcomments.model.GetCommentsResponseWithPresencePublicComment;

public class FastCommentsView extends LinearLayout {

    private RecyclerView recyclerView;
    private CommentsAdapter adapter;
    private CommentFormView commentForm;
    private ProgressBar progressBar;
    private TextView emptyStateView;
    private FastCommentsSDK sdk;
    // TODO maintain relative comment dates

    public FastCommentsView(Context context, FastCommentsSDK sdk) {
        super(context);
        init(context, null, sdk);
    }

    public FastCommentsView(Context context, AttributeSet attrs, FastCommentsSDK sdk) {
        super(context, attrs);
        init(context, attrs, sdk);
    }

    public FastCommentsView(Context context, AttributeSet attrs, int defStyleAttr, FastCommentsSDK sdk) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, sdk);
    }

    private void init(Context context, AttributeSet attrs, FastCommentsSDK sdk) {
        setOrientation(VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.fast_comments_view, this, true);
        
        recyclerView = findViewById(R.id.recyclerViewComments);
        progressBar = findViewById(R.id.commentsProgressBar);
        emptyStateView = findViewById(R.id.emptyStateView);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new CommentsAdapter();
        recyclerView.setAdapter(adapter);
        
        // Add comment form at the bottom
        commentForm = new CommentFormView(context);
        addView(commentForm);
        
        this.sdk = sdk;
    }

    public void load() {
        showLoading(true);

        sdk.getComments(new FCCallback<GetCommentsResponseWithPresencePublicComment>() {
            @Override
            public boolean onFailure(APIError error) {
                showLoading(false);
                showEmptyState(true);
                emptyStateView.setText(R.string.error_loading_comments);
                return CONSUME;
            }

            @Override
            public boolean onSuccess(GetCommentsResponseWithPresencePublicComment response) {
                showLoading(false);

                if (response.getComments().isEmpty()) {
                    showEmptyState(true);
                } else {
                    showEmptyState(false);
                    adapter.setComments(sdk.commentsTree);
                }
                return CONSUME;
            }
        });
    }
    
    /**
     * Post a new comment
     * @param commentText Text of the comment
     * @param parentId Parent comment ID for replies (null for top-level comments)
     */
    public void postComment(String commentText, String parentId) {
        commentForm.setSubmitting(true);
        
//        sdk.postComment(commentText, parentId, new FastCommentsSDK.CommentPostCallback() {
//            @Override
//            public void onSuccess(APICommentPublicComment comment) {
//                commentForm.setSubmitting(false);
//                commentForm.clearText();
//
//                // Refresh comments to include the new one
//                loadComments();
//            }
//
//            @Override
//            public void onError(Exception e) {
//                commentForm.setSubmitting(false);
//                commentForm.showError(e.getMessage());
//            }
//        });
    }
    
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }
    
    private void showEmptyState(boolean show) {
        emptyStateView.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }
    
    /**
     * Public method to manually reload comments
     */
    public void refresh() {
        load();
    }
}